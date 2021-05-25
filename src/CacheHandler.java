import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.time.Instant;
import java.lang.Thread;

class CacheHandler {
    private static final int MAX_CACHE_DURATION = 1800; // 1800 s = 30 min
    private static final String CACHE_DIR = "cache";
    private static final int CACHE_CLEAR_TIME = 21600; // 6 Stunden
    private static long lastCacheClear = Instant.now().getEpochSecond();

    public static String retrieveCachedArticle(String articleUrl) { // null, wenn Zeit abgelaufen oder nicht vorhanden
        String filename = getCachedName(articleUrl);
        try {
            File dir = new File(CACHE_DIR);
            if(!dir.isDirectory()) {
                dir.mkdir();
            }

            File file = new File(filename);
            
            if(isInvalid(file)) {
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

    public static void asyncHandleCacheClear() {
        long currentUnixTime = Instant.now().getEpochSecond();
        if(lastCacheClear + CACHE_CLEAR_TIME < currentUnixTime) {
            new ClearCacheThread().start();
            lastCacheClear = currentUnixTime;
        }
    }

    // Das muss wegen des Threads public sein
    public static void clearCache() throws IOException {
        File cacheDir = new File(CACHE_DIR);
        for(File file : cacheDir.listFiles()) {
            if(isInvalid(file)) {
                file.delete();
            }
        }
    }

    private static String getCachedName(String articleUrl) {
        String filename = articleUrl.replace("https://", "").replace("/", ".") + ".gmi";
        return CACHE_DIR + File.separatorChar + filename;
    }

    private static boolean isInvalid(File file) {
        long lastModified = file.lastModified() / 1000; // ms zu s
        long currentUnixTime = Instant.now().getEpochSecond();
        if(lastModified + MAX_CACHE_DURATION < currentUnixTime) {
            return true;
        }

        return false;
    }
}

class ClearCacheThread extends Thread {
    @Override
    public void run() {
        try {
            CacheHandler.clearCache();
        }
        catch(Exception _e) {}
    }
}