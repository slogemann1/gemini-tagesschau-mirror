import org.json.JSONObject;
import org.json.JSONArray;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

class PageGenorator {
    private static final String GEMINI_URL = "gemini://127.0.0.1";
    private RequestHandler rq;
    
    public PageGenorator(RequestHandler rq) {
        this.rq = rq;
    }

    public String generateHomepage() throws AppException {
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

    public String generateNewsPage(String verifiedSafeUrl) throws AppException {
        String page = "";
        
        try {
            // Sende Anfrage
            JSONObject newsArticle = rq.executePreformedRequest(verifiedSafeUrl);
            JSONArray content = newsArticle.getJSONArray("content");

            // Zeit und Datum
            String dateAndTime = newsArticle.getString("date");
            String date = dateAndTime.substring(0, 10);
            String time = dateAndTime.substring(11, 11 + 5);

            // Anfang
            page += "#" + newsArticle.getString("title") + "\nTEXT_INFO_LINE_GOES_HERE\n\n";

            // Füge alle Paragraphen hinzu
            String reporterLine = "";
            for(Object paragraphObj : content) {
                JSONObject paragraph = (JSONObject)paragraphObj;

                String paraType = paragraph.getString("type");
                if(paraType.equals("text")) {
                    String paraText = paragraph.getString("value");
                    if(paraText.startsWith("<em>")) { // Für Reporter
                        reporterLine = removeTags(paraText) + " "; // Leerzeichen wegen Datum
                    }
                    else if(paraText.startsWith("<strong>Über dieses Thema berichtete")) { // Extra Zeile am Ende, weil es inhaltlich nicht zum Artikel gehört
                        page += "\n" + removeTags(paraText) + "\n";
                    }
                    else {
                        page += removeTags(paraText) + "\n";
                    }
                }
                else if(paraType.equals("headline")) {
                    page += "###" + removeTags(paragraph.getString("value")) + "\n";
                }
            }

            // Info über den Text
            String textInfoLine = String.format("%s%s %s", reporterLine, date, time);
            page = page.replace("TEXT_INFO_LINE_GOES_HERE", textInfoLine);
        }
        catch(Exception e) {
            throw new MissingJsonValueException(e.toString());
        }

        return page;
    }

    private static String removeTags(String str) {
        return str.replaceAll("<[\\w\\W]*?>", "");
    }
}

class MissingJsonValueException extends AppException {
    MissingJsonValueException(String details) { super(details); }
}