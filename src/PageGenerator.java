import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
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

            homepage += "## Ressort\n";
            homepage += "=>" + GEMINI_DOMAIN + "/topic?inland Inland\n";
            homepage += "=>" + GEMINI_DOMAIN + "/topic?ausland Ausland\n";
            homepage += "=>" + GEMINI_DOMAIN + "/topic?wirtschaft Wirtschaft\n";
            homepage += "=>" + GEMINI_DOMAIN + "/topic?sport Sport\n";
            homepage += "=>" + GEMINI_DOMAIN + "/topic?video Video\n";
        }
        catch (JSONException e) {
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
        catch(JSONException e) {
            throw new MissingJsonValueException(e);
        }

        return regionalHomepage;
    }

    public String generateSearchPage(String query, int pageNumber) throws AppException {
        String page = "# Suchergebnisse für '" + query + "'\n\n";

        try {
            JSONObject resultsContainer = rq.getSearchResults(query, pageNumber);
            JSONArray results = resultsContainer.getJSONArray("searchResults");

            if(results.length() == 0) {
                page += "Leider keine Suchergebnisse für '" + query + "' gefunden\n";
                page += String.format("=>%s/search Neue Suche\n", GEMINI_DOMAIN);
            }

            int i = 1;
            int pageSize = rq.getSearchPageSize();
            for(Object resultObj : results) {
                JSONObject result = (JSONObject)resultObj;
                
                String dateAndTime = result.getString("date");
                String date = timeAndDateFromIso8601(dateAndTime)[0];

                String title = result.getString("title");

                String link = result.getString("details");
                String newLink = GEMINI_DOMAIN + "/do-request?" + link;

                page += String.format("=>%s [%d] %s\n", newLink, i + pageNumber * pageSize, title);
                
                if(result.has("firstSentence")) {
                    page += String.format("%s - %s\n\n", date, result.getString("firstSentence"));
                }
                else {
                    page += date + "\n\n";
                }

                i++;
            }

            int totalResultCount = resultsContainer.getInt("totalItemCount");
            String urlEncodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            if(pageSize * (pageNumber + 1) < totalResultCount) {
                String nextPageLink = String.format("%s/search?%s&page=%d", GEMINI_DOMAIN, urlEncodedQuery, pageNumber + 1);
                page += "\n=>" + nextPageLink + " Nächste Seite";
            }
            if(pageNumber != 0) {
                String nextPageLink = String.format("%s/search?%s&page=%d", GEMINI_DOMAIN, urlEncodedQuery, pageNumber - 1);
                page += "\n=>" + nextPageLink + " Vorherrige Seite";
            }

            return page;
        }
        catch(JSONException e) {
            throw new MissingJsonValueException(e);
        }
    }

    public String generateTopicHomepage(String validTopicName) throws AppException {
        Topic topic = Topic.fromValidName(validTopicName);
        JSONObject news = rq.getNews(new Region[] {}, topic);
        
        char firstChar = validTopicName.charAt(0);
        char firstCharCaps = (char)((short)firstChar - 32);
        String topicName = validTopicName.substring(1, validTopicName.length());
        topicName = firstCharCaps + topicName;

        String topicHomepage = "# Tagesschau - " + topicName + "\n\n";
        
        try {
            String sectionTitle = "Aktuelle Nachrichten - " + getCurrentDate();
            JSONArray articleList = news.getJSONArray("news");
            topicHomepage += generateArticleList(sectionTitle, articleList);
        }
        catch(JSONException e) {
            throw new MissingJsonValueException(e);
        }

        return topicHomepage;
    }

    public String generateNewsPage(String verifiedSafeUrl) throws AppException {
        String page = "";
        
        try {
            // Sende Anfrage
            JSONObject newsArticle = rq.executePreformedRequest(verifiedSafeUrl);
            JSONArray content = newsArticle.getJSONArray("content");

            // Zeit und Datum
            String dateAndTime = newsArticle.getString("date");
            String[] dateAndTimeParsed = timeAndDateFromIso8601(dateAndTime);
            String date = dateAndTimeParsed[0];
            String time = dateAndTimeParsed[1];

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
                else if(paraType.equals("htmlEmbed")) {
                    page += "=>" + paragraph.getJSONObject("htmlEmbed").getString("url") + " Externe Webseite\n";
                }
            }

            // Info über den Text
            String textInfoLine = String.format("%s%s %s", reporterLine, date, time);
            page = page.replace("TEXT_INFO_LINE_GOES_HERE", textInfoLine);
        }
        catch(JSONException e) {
            throw new MissingJsonValueException(e);
        }

        return page;
    }

    private String generateArticleList(String title, JSONArray articleList) throws JSONException {
        String page = "## " + title + "\n";
        
        for(Object articleObj : articleList) {
            JSONObject article = (JSONObject)articleObj;

            boolean isVideo = false;
            if(article.has("type") && article.getString("type").equals("video")) {
                isVideo = true;
            }

            // Erstelle neue Url
            String newLink;
            if(!isVideo) {
                String newApiRequest = article.getString("details"); // Wichtig!: Adresse muss geprüft werden
                newLink = GEMINI_DOMAIN + "/do-request?" + newApiRequest; // Eigene "do-request" Endpoint
            }
            else {
                newLink = article.getJSONObject("streams").getString("h264m");
            }

            // Zeit der Publikation
            String[] timeAndDate = timeAndDateFromIso8601(article.getString("date"));
            String date = timeAndDate[0];
            String time = timeAndDate[1];
            
            if(!isVideo) {
                // Füge Link mit Titel ein und erster Satz hinzu
                page += String.format("=>%s %s\n", newLink, article.getString("title"));
                if(article.has("firstSentence")) {
                    page += String.format("%s - %s\n\n", time, article.get("firstSentence"));
                }
                else {
                    page += "\n";
                }
            }
            else {
                String newTitle = article.getString("title").replace("tagesschau", "Tagesschau"); // "Tagesschau" groß schreiben, damit es besser aussieht, wenn es als erstes Wort kommt
                page += String.format("=>%s %s - %s %s\n\n", newLink, newTitle, date, time);
            }
        }

        return page;
    }

    private String generateRegionalArticleList(String title, JSONArray articleList) throws JSONException {
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

    private static String removeTags(String str) {
        return str.replaceAll("<[\\w\\W]*?>", "");
    }

    private static String[] timeAndDateFromIso8601(String formattedDateTime) {
        String[] dateAndTime = new String[2];
        dateAndTime[0] = formattedDateTime.substring(0, 10);
        dateAndTime[1] = formattedDateTime.substring(11, 11 + 5);
        return dateAndTime;
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