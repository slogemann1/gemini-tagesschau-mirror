import org.json.JSONObject;

class Program {
    public static void main(String[] args) {
        String jsonString = "{ \"text\": \"Hello World!\" }";

        try {
            JSONObject jsonObj = new JSONObject(jsonString);

            System.out.println(jsonObj.get("text"));
        }
        catch (Exception _e) { System.out.println("Failed to parse JSON"); }
    }
}