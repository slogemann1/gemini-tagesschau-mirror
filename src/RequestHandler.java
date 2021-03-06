import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.lang.InterruptedException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.json.JSONObject;
import org.json.JSONException;

class RequestHandler {
    private static final int SEARCH_PAGE_SIZE = 15;
    private static final String API_URL = "https://www.tagesschau.de/api2";

    private HttpClient client;

    public RequestHandler() {
        this.client = HttpClient.newHttpClient();
    }

    public JSONObject getSearchResults(String query, int page) throws ApiRequestFailureException {
        RequestParameter[] params = new RequestParameter[] {
            new RequestParameter("searchText", query),
            new RequestParameter("resultPage", page + ""),
            new RequestParameter("pageSize", SEARCH_PAGE_SIZE + ""),
            new RequestParameter("type", "story")
        };

        // Sende die Anfrage mit den Parametern
        JSONObject response = sendRequest("search", params);
        return response;
    }

    public JSONObject getNews(Region[] regions, Topic topic) throws ApiRequestFailureException {
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

    public JSONObject getHompage() throws ApiRequestFailureException {
        // Sende die Anfrage
        JSONObject response = sendRequest("homepage", new RequestParameter[0]);
        return response;
    }

    public JSONObject executePreformedRequest(String verifiedSafeUrl) throws ApiRequestFailureException {
        try {
            // Erstelle und sende Anfrage
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(verifiedSafeUrl))
                .build();
            HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject parsedObject = new JSONObject(response.body());
            return parsedObject;
        }
        catch(JSONException | IOException | InterruptedException e) {
            throw new ApiRequestFailureException(e);
        }
    }

    public int getSearchPageSize() {
        return SEARCH_PAGE_SIZE;
    }

    private JSONObject sendRequest(String endpoint, RequestParameter[] params) throws ApiRequestFailureException {
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
        catch(JSONException | IOException | InterruptedException e) {
            throw new ApiRequestFailureException(e);
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
    private static final String[] nameLookupTable = new String[] {
        "Baden-Württemberg",
        "Bayern",
        "Berlin",
        "Brandenburg",
        "Bremen",
        "Hamburg",
        "Hessen",
        "Mecklenburg-Vorpommern",
        "Niedersachsen",
        "Nordrhein-Westfahlen",
        "Reinland-Pfalz",
        "Saarland",
        "Sachsen",
        "Sachsen-Anhalt",
        "Schleswig-Holstein",
        "Thüringen"
    };
    private static final Region[] regionLookupTable = new Region[] {
        BADEN_WUERTTEMBERG,
        BAYERN,
        BERLIN,
        BRANDENBURG,
        BREMEN,
        HAMBURG,
        HESSEN,
        MECKLENBURG_VORPOMMERN,
        NIEDERSACHSEN,
        NORDRHEIN_WESTFAHLEN,
        REINLAND_PFALZ,
        SAARLAND,
        SACHSEN,
        SACHSEN_ANHALT,
        SCHLESWIG_HOLSTEIN,
        THUERINGEN
    };

    private Region(int regionId) {
        this.regionId = regionId;
    }

    public static Region uncheckedFromId(int regionId) {
        return regionLookupTable[regionId - 1];
    }

    public static String uncheckedNameFromId(int regionId) {
        return nameLookupTable[regionId - 1];
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

    public static boolean isValidName(String topicName) {
        for(Topic t : Topic.values()) {
            if(t.topicName.equals(topicName)) {
                return true;
            }
        }
        return false;
    }

    public static Topic fromValidName(String topicName) {
        for(Topic t : Topic.values()) {
            if(t.topicName.equals(topicName)) {
                return t;
            }
        }
        return null;
    }
}

class ApiRequestFailureException extends AppException {
    ApiRequestFailureException(String details) { super(details); }
    ApiRequestFailureException(Exception e) { super(e); }
    ApiRequestFailureException(String details, Exception e) { super(details, e); };

    @Override
    protected String defaultMessage() { return "An error ocurred when making a request to the api"; }
}