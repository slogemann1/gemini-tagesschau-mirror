import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;

class Program {
    public static void main(String[] args) throws IOException {
        String outfilePath = null;
        String action = null; // Possible values: doRequest, getHomepage
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
            else {
                returnCgiError(outfilePath);
            }
        }
        catch(AppException e) {
            writeToFile(outfilePath, e.toString() + ": " + e.getDetails());

            int exitCode = 42;
            if(e instanceof UnauthorizedRequestException) {
                exitCode = 53; // Server akzeptiert die Proxy-Anfrage nicht
            }
            else if(e instanceof ApiRequestFailureExpection) {
                exitCode = 43; // Proxy Fehler
            }
            else if(e instanceof MissingJsonValueException) {
                exitCode = 42; // Cgi Fehler
            }

            System.exit(exitCode);
        }

        writeToFile(outfilePath, fileText);
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
}

// Expection Type
class AppException extends Exception {
    private String details;

    AppException(String details) {
        this.details = details;
    }

    public String getDetails() {
        return this.details;
    }
}

class UnauthorizedRequestException extends AppException {
    UnauthorizedRequestException(String details) { super(details); }
}