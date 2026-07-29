import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class SerpFetcher {

    private static final String TAVILY_URL = "https://api.tavily.com/search";
    private final String apiKey;
    private final HttpClient client = HttpClient.newHttpClient();

    public SerpFetcher(String apiKey) {
        this.apiKey = apiKey;
    }

    public List<String> getResultUrls(String query, int maxResults) {
        List<String> urls = new ArrayList<>();

        JSONObject requestBody = new JSONObject();
        requestBody.put("api_key", apiKey);
        requestBody.put("query", query);
        requestBody.put("max_results", maxResults);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TAVILY_URL))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(requestBody.toString()))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("Search API error: " + response.statusCode() + " " + response.body());
                return urls;
            }

            JSONObject json = new JSONObject(response.body());
            JSONArray results = json.optJSONArray("results");

            if (results != null) {
                for (int i = 0; i < results.length(); i++) {
                    urls.add(results.getJSONObject(i).getString("url"));
                }
            }

        } catch (Exception e) {
            System.err.println("Failed to fetch search results: " + e.getMessage());
        }

        return urls;
    }
}