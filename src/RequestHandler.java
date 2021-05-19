import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.json.JSONObject;

class RequestHandler {
    private static final int SEARCH_PAGE_SIZE = 15;
    private static final String API_URL = "https://www.tagesschau.de/api2";

    private HttpClient client;

    public RequestHandler() {
        this.client = HttpClient.newHttpClient();
    }

    public JSONObject getSearchResults(String query, int page) throws ApiRequestFailureExpection {
        RequestParameter[] params = new RequestParameter[] {
            new RequestParameter("searchText", query),
            new RequestParameter("resultPage", page + ""),
            new RequestParameter("pageSize", SEARCH_PAGE_SIZE + "")
        };

        // Sende die Anfrage mit den Parametern
        JSONObject response = sendRequest("search", params);
        return response;
    }

    public JSONObject getNews(Region[] regions, Topic topic) throws ApiRequestFailureExpection {
        ArrayList<RequestParameter> params = new ArrayList<RequestParameter>();

        // Füge Regionen hinzu
        if(regions.length != 0) {
            String regionList = "";
            for(Region region : regions) {
                regionList += region.regionId + ",";
            }
            regionList = regionList.substring(0, regionList.length() - 1);

            params.add(new RequestParameter("regions", regionList));
        }

        // Füge Kategorie hinzu
        if(topic != null) {
            params.add(new RequestParameter("ressort", topic.topicName));
        }

        // Mache aus dem ArrayList einen Array
        RequestParameter[] paramArray = new RequestParameter[params.size()];
        params.toArray(paramArray);

        // Sende die Anfrage
        JSONObject response = sendRequest("news", paramArray);
        return response;
    }

    public JSONObject getHompage() throws ApiRequestFailureExpection {
        // Sende die Anfrage
        JSONObject response = sendRequest("homepage", new RequestParameter[0]);
        return response;
    }

    public JSONObject executePreformedRequest(String verifiedSafeUrl) throws ApiRequestFailureExpection {
        try {
            // Erstelle und sende Anfrage
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(verifiedSafeUrl))
                .build();
            HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject parsedObject = new JSONObject(response.body());
            return parsedObject;
        }
        catch(Exception e) {
            throw new ApiRequestFailureExpection(e.toString());
        }
    }

    private JSONObject sendRequest(String endpoint, RequestParameter[] params) throws ApiRequestFailureExpection {
        try {
            // Erstelle url für die Anfrage
            String url = API_URL + "/" + endpoint + "/";
            if(params.length != 0) {
                url += "?";

                for(RequestParameter param : params) {
                    url += URLEncoder.encode(param.parameter, StandardCharsets.UTF_8.toString());
                    url += "=" + URLEncoder.encode(param.value, StandardCharsets.UTF_8.toString()) + "&";
                }
                url = url.substring(0, url.length() - 1);
            }

            // Erstelle und sende die Anfrage
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();
            HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());

            // Erstelle ein JSONObject aus der Antwort
            JSONObject parsedObject = new JSONObject(response.body());
            return parsedObject;
        }
        catch(Exception e) {
            throw new ApiRequestFailureExpection(e.toString());
        }
    }

    private class RequestParameter {
        String parameter;
        String value;

        RequestParameter(String parameter, String value) {
            this.parameter = parameter;
            this.value = value;
        }
    }
}

enum Region {
    BADEN_WUERTTEMBERG(1),
    BAYERN(2),
    BERLIN(3),
    BRANDENBURG(4),
    BREMEN(5),
    HAMBURG(6),
    HESSEN(7),
    MECKLENBURG_VORPOMMERN(8),
    NIEDERSACHSEN(9),
    NORDRHEIN_WESTFAHLEN(10),
    REINLAND_PFALZ(11),
    SAARLAND(12),
    SACHSEN(13),
    SACHSEN_ANHALT(14),
    SCHLESWIG_HOLSTEIN(15),
    THUERINGEN(16);

    public final int regionId;

    private Region(int regionId) {
        this.regionId = regionId;
    }
}

enum Topic {
    INLAND("inland"),
    AUSLAND("ausland"),
    WIRTSCHAFT("wirtschaft"),
    SPORT("sport"),
    VIDEO("video");

    public final String topicName;

    private Topic(String topicName) {
        this.topicName = topicName;
    }
}

class ApiRequestFailureExpection extends AppException {
    ApiRequestFailureExpection(String details) { super(details); }
}