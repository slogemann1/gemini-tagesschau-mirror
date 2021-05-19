import org.json.JSONObject;
import org.json.JSONArray;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

class PageGenorator {
    private static final String GEMINI_URL = "gemini://localhost";
    private RequestHandler rq;
    
    public PageGenorator(RequestHandler rq) {
        this.rq = rq;
    }

    public String generateHompage() throws AppException {
        JSONObject homepageJson = rq.getHompage();
        
        // Datum
        ZoneId germanyTZ = ZoneId.of("GMT+02:00");
        LocalDateTime currentDate = LocalDateTime.now(germanyTZ);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String formattedDate = currentDate.format(formatter);


        // Titel
        String homepage = "# Tagesschau\n\n"
        + "## Aktuelle Nachrichten - " + formattedDate + "\n";

        try {
            // Füge alle Nachrichten hinzu
            JSONArray newsArr = homepageJson.getJSONArray("news");
            for(Object newsObj : newsArr) {
                JSONObject news = (JSONObject)newsObj;

                // Erstelle neue Url
                String newApiRequest = news.getString("details"); // Wichtig!: Adresse muss geprüft werden
                String newLink = GEMINI_URL + "/do-request?" + newApiRequest; // Eigene "do-request" Endpoint

                // Zeit der Publikation
                String time = news.getString("date").substring(11, 11+5);
 
                // Füge Link mit Titel ein und erster Satz hinzu
                homepage += String.format("=>%s %s\n", newLink, news.getString("title"));
                if(news.has("firstSentence")) {
                    homepage += String.format("%s - %s \n\n", time, news.get("firstSentence"));
                }
                else {
                    homepage += "\n";
                }
            }
        }
        catch (Exception e) {
            throw new MissingJsonValueException(e.toString());
        }

        return homepage;
    }
}

class MissingJsonValueException extends AppException {
    MissingJsonValueException(String details) { super(details); }
}