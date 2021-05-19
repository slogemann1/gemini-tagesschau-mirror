import org.json.JSONObject;

class Program {
    public static void main(String[] args) {
        
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