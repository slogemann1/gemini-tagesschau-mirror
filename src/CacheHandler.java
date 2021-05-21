import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.time.Instant;

class CacheHandler {
    private static final int MAX_CACHE_DURATION = 1800; // 1800 s = 30 min
    private static final String CACHE_DIR = "cache";

    public static String retrieveCachedArticle(String articleUrl) { // null, wenn Zeit abgelaufen oder nicht vorhanden
        String filename = getCachedName(articleUrl);
        try {
            File dir = new File(CACHE_DIR);
            if(!dir.isDirectory()) {
                dir.mkdir();
            }

            File file = new File(filename);
            
            long lastModified = file.lastModified();
            long currentUnixTime = Instant.now().getEpochSecond();
            if(lastModified + MAX_CACHE_DURATION < currentUnixTime) {
                file.delete();
                return null;
            }

            FileReader fr = new FileReader(filename);
            BufferedReader br = new BufferedReader(fr);
            
            String articleContents = "";
            String currentLine = br.readLine();
            while(currentLine != null) {
                articleContents += currentLine + "\n";
                currentLine = br.readLine();
            }

            return articleContents;
        }
        catch(IOException _e) {
            return null;
        }
    }

    public static void cacheArticle(String articleUrl, String articleContents) {
        String filename = getCachedName(articleUrl);

        try {
            FileWriter fw = new FileWriter(filename);
            BufferedWriter br = new BufferedWriter(fw);

            br.write(articleContents);
            br.close();
        }
        catch(IOException _e) {}
    }

    private static String getCachedName(String articleUrl) {
        String filename = articleUrl.replace("https://", "").replace("/", ".") + ".gmi";
        return CACHE_DIR + File.separatorChar + filename;
    }
}