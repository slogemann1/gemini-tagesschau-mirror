import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.net.ServerSocket;
import java.net.Socket;
import java.lang.Thread;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.ArrayList;

class Program {
    private static final String LOG_FILE = "log.txt";
    private static final int PORT = 5555;

    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(PORT);
        while(true) {
            try {
                Socket client = server.accept();
                ServerThread thread = new ServerThread(client); // Starte einen neuen Thread, der die argumente einliest und run() damit ausführt

                CacheHandler.asyncHandleCacheClear(); // Falls Dateien aus dem Cache gelöscht werden sollen, starte einen neuen Thread

                thread.start();
            }
            catch(Exception _e) { continue; } // Ignoriere Fehler
        }
    }

    public static int run(String[] args) throws IOException {
        String outfilePath = null;
        String action = null; // Possible values: doRequest, getHomepage, getRegional, getSearch, getTopic
        String query = null;

        // Lese argumente
        for(String arg : args) {
            if(arg.startsWith("unique_file_path='")) {
                outfilePath = arg.split("unique_file_path='")[1];
                outfilePath = outfilePath.substring(0, outfilePath.length() - 1);
            }
            else if(arg.startsWith("action='")) {
                action = arg.split("action='")[1];
                action = action.substring(0, action.length() - 1);
            }
            else if(arg.startsWith("query='")) {
                String[] argSplit = arg.split("query='");
                query = argSplit.length > 1 ? argSplit[1] : "";
                query = query.substring(0, query.length() - 1);
                query = URLDecoder.decode(query, StandardCharsets.UTF_8.toString());
            }
        }

        if(outfilePath == null || action == null) {
            returnCgiError(outfilePath);
        }

        // Erstelle Objekte
        RequestHandler rq = new RequestHandler();
        PageGenorator pg = new PageGenorator(rq);

        // Führe die gegebene aktion aus
        String fileText = null;
        try {
            if(action.equals("getHomepage")) {
                fileText = pg.generateHomepage();
            }
            else if(action.equals("doRequest")) {
                fileText = handleDoRequest(pg, query);
            }
            else if(action.equals("getRegional")) {
                fileText = handleRegionalRequest(pg, query);
            }
            else if(action.equals("getSearch")) {
                fileText = handleSearchRequest(pg, query);
            }
            else if(action.equals("getTopic")) {
                fileText = handleTopicRequest(pg, query);
            }
            else {
                returnCgiError(outfilePath);
            }
        }
        catch(AppException e) {
            writeToFile(outfilePath, e.toString() + ": " + e.getDetails());
            log(e.toString() + ": " + e.getInternalDetails());

            int exitCode = 42;
            if(e instanceof UnauthorizedRequestException) {
                exitCode = 53; // Server akzeptiert die Proxy-Anfrage nicht
            }
            else if(e instanceof ApiRequestFailureException) {
                exitCode = 43; // Proxy Fehler
            }
            else if(e instanceof MissingJsonValueException) {
                exitCode = 42; // Cgi Fehler
            }
            else if(e instanceof InvalidRequestQueryException) {
                exitCode = 42; // Cgi Fehler (es gibt keinen passenderen Fehler)
            }

            return exitCode;
        }

        writeToFile(outfilePath, fileText);
        return 0;
    }

    static String handleTopicRequest(PageGenorator pg, String query) throws AppException {
        if(!Topic.isValidName(query)) {
            throw new InvalidRequestQueryException("The \"/topic\" endpoint is only to be used internally and accepts only specific topic parameters");
        }

        String cacheResult = CacheHandler.retrieveCachedArticle(query);
        if(cacheResult == null) {
            String generatedPage = pg.generateTopicHomepage(query);
            CacheHandler.cacheArticle(query, generatedPage);
            return generatedPage;
        }
        else {
            return cacheResult;
        }
    }

    static String handleSearchRequest(PageGenorator pg, String query) throws AppException {
        int page;
        String actualQuery;
        
        String[] queryAndPage = query.split("&page=");
        if(queryAndPage.length > 1) {
            try {
                actualQuery = queryAndPage[0];
                page = Integer.parseInt(queryAndPage[1]);
                if(page < 0) {
                    throw new InvalidRequestQueryException("The \"/search\" endpoint only accepts positive page numbers");
                }
            }
            catch(NumberFormatException e) {
                throw new InvalidRequestQueryException("The \"/search\" endpoint only accepts numbers with the page parameter", e);
            }
        }
        else {
            actualQuery = query;
            page = 0;
        }

        String generatedPage = pg.generateSearchPage(actualQuery, page);
        return generatedPage;
    }

    static String handleRegionalRequest(PageGenorator pg, String query) throws AppException {
        try {
            int regionId = Integer.parseInt(query);
            if(regionId > 17 || regionId < 1) {
                throw new InvalidRequestQueryException("The \"/regional\" endpoint is only to be used internally and only accepts numbers from 1 to 16");
            }

            String articleName = "regional-" + query;
            String cacheResult = CacheHandler.retrieveCachedArticle(articleName);
            if(cacheResult != null) {
                return cacheResult;
            }
            else {
                String generatedPage = pg.generateRegionalHomepage(regionId);
                CacheHandler.cacheArticle(articleName, generatedPage);
                return generatedPage;
            }
        }
        catch(NumberFormatException e) {
            throw new InvalidRequestQueryException("The \"/regional\" endpoint is only to be used internally and only accepts numbers as parameters", e);
        }
    }

    static String handleDoRequest(PageGenorator pg, String queryUrl) throws AppException {
        if(queryUrl.startsWith("https://www.tagesschau.de")
        || queryUrl.startsWith("https://wetter.tagesschau.de")) {
            String cacheResult = CacheHandler.retrieveCachedArticle(queryUrl);
            if(cacheResult != null) {
                return cacheResult;
            }
            else {
                String articleContents = pg.generateNewsPage(queryUrl);
                CacheHandler.cacheArticle(queryUrl, articleContents);
                return articleContents;
            }
        }
        else {
            throw new UnauthorizedRequestException("The requested request url " + queryUrl + " is not whitelisted");
        }
    }

    static void returnCgiError(String nullableFilename) throws IOException {
        String errMsg = "Invalid arguments from server";
        if(nullableFilename != null) writeToFile(nullableFilename, errMsg);
        else System.out.println(errMsg);
        System.exit(42); // CGI Fehler
    }

    static void writeToFile(String filename, String text) throws IOException {
        FileWriter fw = new FileWriter(filename);
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(text);
        bw.close();
    }

    static void log(String message) {
        try {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String timeFormatted = formatter.format(now);

            FileWriter fw = new FileWriter(LOG_FILE, true);
            fw.write(timeFormatted + " | " + message + "\n");
            fw.close();
        }
        catch(IOException e) {
            System.out.println("Failed to log " + e);
        }
    }
}

class ServerThread extends Thread {
    Socket client;

    public ServerThread(Socket client) {
        this.client = client;
    }

    @Override
    public void run() {
        try {
            OutputStream writer = client.getOutputStream();
            InputStream reader = client.getInputStream();

            byte[] buffer = new byte[1024];
            int bytesRead = reader.read(buffer);
            String[] args = stringArrayFromCEncoding(buffer, bytesRead);
            
            int exitCode = Program.run(args);
            writer.write((byte)exitCode);

            writer.close();
            reader.close();
        }
        catch(IOException e) {
            Program.log(e.toString());
        }
    }

    private String[] stringArrayFromCEncoding(byte[] buffer, int bufferSize) {
        ArrayList<String> strings = new ArrayList<String>();
        
        String currString = "";
        for(int i = 0; i < bufferSize; i++) {
            if(buffer[i] != 0) {
                currString += (char)buffer[i];
            }
            else {
                strings.add(currString);
                currString = "";
            }
        }

        return strings.toArray(new String[strings.size()]);
    }
}

// Expection Type
class AppException extends Exception {
    private String details;
    private String internalDetails;

    private void setInternalDetailsWithException(Exception e) {
        if(e instanceof AppException) {
            this.internalDetails = ((AppException)e).getInternalDetails();
        }
        else {
            this.internalDetails = e.toString() + " { " + e + " }";
        }
    }

    public AppException(String details, Exception e) {
        this.details = details;
        this.setInternalDetailsWithException(e);
    }

    public AppException(String details) {
        this.details = details;
        this.internalDetails = details;
    }

    public AppException(Exception e) {
        this.details = defaultMessage();
        this.setInternalDetailsWithException(e);
    }

    protected String defaultMessage() {
        return "An error has occured within the application";
    }

    public String getDetails() {
        return this.details;
    }

    public String getInternalDetails() {
        return this.internalDetails;
    }
}

class UnauthorizedRequestException extends AppException {
    public UnauthorizedRequestException(String details, Exception e) { super(details, e); }
    public UnauthorizedRequestException(String details) { super(details); }
    public UnauthorizedRequestException(Exception e) { super(e); }
}

class InvalidRequestQueryException extends AppException {
    public InvalidRequestQueryException(String details, Exception e) { super(details, e); }
    public InvalidRequestQueryException(String details) { super(details); }
    public InvalidRequestQueryException(Exception e) { super(e); }
}