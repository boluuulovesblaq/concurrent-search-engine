import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONObject;

public class SemanticExtractor {

    private final HttpClient client = HttpClient.newHttpClient();
    private final String apiKey;
    private final boolean mockMode;
    private static final String MODEL = "claude-sonnet-4-5-20250929";

    private static final List<String> MOCK_FEATURES = List.of(
            "GPS tagging", "anonymous reporting", "media upload", "real-time alerts",
            "SMS notifications", "map-based visualization", "user verification", "offline mode"
    );
    private static final List<String> MOCK_HEADINGS = List.of(
            "Introduction", "Related Work", "Methodology", "Architecture",
            "Evaluation", "Results", "Discussion", "Conclusion"
    );

    public enum Mode {
        CRIME_FEATURES("List the distinctive system features this paper describes "
                + "(e.g. GPS tagging, anonymous reporting, media upload, real-time alerts). "
                + "Use short, consistent labels (2-4 words each) so the same feature named "
                + "slightly differently across papers still matches."),
        DL_HEADINGS("List the section sub-headings used in this paper "
                + "(e.g. Introduction, Related Work, Architecture, Evaluation). "
                + "Use the paper's own heading wording, normalized to short consistent labels.");

        private final String instruction;

        Mode(String instruction) {
            this.instruction = instruction;
        }

        public String getInstruction() {
            return instruction;
        }
    }

    public SemanticExtractor(String apiKey) {
        this(apiKey, false);
    }

    public SemanticExtractor(String apiKey, boolean mockMode) {
        this.apiKey = apiKey;
        this.mockMode = mockMode;
    }

    public List<String> extract(String pageText, Mode mode) {
        if (mockMode) {
            return mockExtract(mode);
        }

        try {
            String userMessage = mode.getInstruction()
                    + "\n\nRespond with ONLY a JSON array of short strings, nothing else. "
                    + "Example: [\"item one\", \"item two\"]. If nothing relevant is found, respond with [].\n\n"
                    + "Text:\n" + truncate(pageText, 8000);

            JSONObject body = new JSONObject()
                    .put("model", MODEL)
                    .put("max_tokens", 500)
                    .put("messages", new JSONArray()
                            .put(new JSONObject()
                                    .put("role", "user")
                                    .put("content", userMessage)));

            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.out.println("Claude API error: status " + response.statusCode() + " - " + response.body());
                return List.of();
            }

            JSONObject json = new JSONObject(response.body());
            String rawText = json.getJSONArray("content")
                    .getJSONObject(0)
                    .getString("text")
                    .trim();

            return parseJsonArrayOfStrings(rawText);

        } catch (Exception e) {
            System.out.println("SemanticExtractor failed: " + e.getMessage());
            return List.of();
        }
    }

    private List<String> mockExtract(Mode mode) {
        List<String> pool = (mode == Mode.CRIME_FEATURES) ? MOCK_FEATURES : MOCK_HEADINGS;
        Random rand = new Random();
        List<String> picked = new ArrayList<>();
        for (String item : pool) {
            if (rand.nextDouble() < 0.6) {
                picked.add(item);
            }
        }
        if (picked.isEmpty()) {
            picked.add(pool.get(0));
        }
        return picked;
    }

    private List<String> parseJsonArrayOfStrings(String rawText) {
        String cleaned = rawText
                .replaceAll("(?s)```json", "")
                .replaceAll("(?s)```", "")
                .trim();

        try {
            JSONArray array = new JSONArray(cleaned);
            List<String> items = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                items.add(array.getString(i));
            }
            return items;
        } catch (Exception e) {
            System.out.println("Could not parse extraction result as JSON array: " + cleaned);
            return List.of();
        }
    }

    private String truncate(String text, int maxChars) {
        if (text == null) return "";
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }
}