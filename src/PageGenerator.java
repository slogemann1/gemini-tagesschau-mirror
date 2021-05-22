import org.json.JSONObject;
import org.json.JSONArray;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

class PageGenorator {
    private static final String GEMINI_DOMAIN = "gemini://127.0.0.1";
    private RequestHandler rq;
    
    public PageGenorator(RequestHandler rq) {
        this.rq = rq;
    }

    public String generateHomepage() throws AppException {
        JSONObject homepageJson = rq.getHompage();
        
        // Datum
        String formattedDate = getCurrentDate();

        // Titel
        String homepage = "# Tagesschau\n\n";

        try {
            // Füge generelle Nachrichten hinzu
            JSONArray newsArr = homepageJson.getJSONArray("news");
            String generalArticlesTitle = "Aktuelle Nachrichten - " + formattedDate;
            homepage += generateArticleList(generalArticlesTitle, newsArr);

            // Füge regionale Auswahl an Nachrichten hinzu
            JSONArray regionalArr = homepageJson.getJSONArray("regional");
            homepage += generateRegionalArticleList("Regional", regionalArr);
            
        }
        catch (Exception e) {
            throw new MissingJsonValueException(e);
        }

        return homepage;
    }

    public String generateRegionalHomepage(int checkedRegionId) throws AppException {
        Region region = Region.uncheckedFromId(checkedRegionId);
        JSONObject regionalNews = rq.getNews(new Region[] { region }, null);
        String regionalHomepage = "# Tagesschau - " + Region.uncheckedNameFromId(region.regionId) + "\n\n";

        try {
            String sectionTitle = "Aktuelle Nachrichten - " + getCurrentDate();
            JSONArray articleList = regionalNews.getJSONArray("news");
            regionalHomepage += generateArticleList(sectionTitle, articleList);
        }
        catch(Exception e) {
            throw new MissingJsonValueException(e);
        }

        return regionalHomepage;
    }

    private String generateArticleList(String title, JSONArray articleList) throws Exception {
        String page = "## " + title + "\n";
        
        for(Object articleObj : articleList) {
            JSONObject article = (JSONObject)articleObj;

            // Erstelle neue Url
            String newApiRequest = article.getString("details"); // Wichtig!: Adresse muss geprüft werden
            String newLink = GEMINI_DOMAIN + "/do-request?" + newApiRequest; // Eigene "do-request" Endpoint

            // Zeit der Publikation
            String time = article.getString("date").substring(11, 11+5);

            // Füge Link mit Titel ein und erster Satz hinzu
            page += String.format("=>%s %s\n", newLink, article.getString("title"));
            if(article.has("firstSentence")) {
                page += String.format("%s - %s \n\n", time, article.get("firstSentence"));
            }
            else {
                page += "\n";
            }
        }

        return page;
    }

    private String generateRegionalArticleList(String title, JSONArray articleList) throws Exception {
        String page = "## " + title + "\n";

        int regionId = 1;
        for(Object articleObj : articleList) {
            JSONObject article = (JSONObject)articleObj;
            
            if(regionId > 16) {
                Program.log("Api may have changed -> see PageGenerator.java at generateRegionalArticleList");
                break;
            }

            String regionalArticlesLink = GEMINI_DOMAIN + "/regional?" + regionId;
            String regionName = Region.uncheckedNameFromId(regionId);

            String articleApiRequest = article.getString("details");
            String articleLink = GEMINI_DOMAIN + "/do-request?" + articleApiRequest;

            page += String.format("### %s\n", regionName);
            page += String.format("=>%s Aktuelle Nachrichten\n", regionalArticlesLink);
            page += String.format("=>%s %s\n\n", articleLink, article.getString("title"));

            regionId++;
        }

        return page;
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
            throw new MissingJsonValueException(e);
        }

        return page;
    }

    private static String removeTags(String str) {
        return str.replaceAll("<[\\w\\W]*?>", "");
    }

    private static String getCurrentDate() {
        ZoneId germanyTZ = ZoneId.of("GMT+02:00");
        LocalDateTime currentDate = LocalDateTime.now(germanyTZ);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String formattedDate = currentDate.format(formatter);
        return formattedDate;
    }
}

class MissingJsonValueException extends AppException {
    MissingJsonValueException(String details, Exception e) { super(details, e); }
    MissingJsonValueException(String details) { super(details); }
    MissingJsonValueException(Exception e) { super(e); }

    @Override
    protected String defaultMessage() { return "An error ocurred while parsing the api response json (this could be an api change)";}
}