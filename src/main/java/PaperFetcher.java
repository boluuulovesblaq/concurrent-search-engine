import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class PaperFetcher {

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();    private final ExecutorService pool;

    public PaperFetcher(ExecutorService pool) {
        this.pool = pool;
    }

    // Fetches and cleans all given URLs CONCURRENTLY.
    public List<String> fetchAll(List<String> urls) {
        List<CompletableFuture<String>> futures = new ArrayList<>();

        for (String url : urls) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                String cleanText = fetchAndClean(url);
                System.out.println("Fetched: " + url + " (" + cleanText.length() + " characters) "
                        + "[thread: " + Thread.currentThread().getName() + "]");
                return cleanText;
            }, pool);

            futures.add(future);
        }

        // Wait here until ALL futures are done
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<String> results = new ArrayList<>();
        for (CompletableFuture<String> f : futures) {
            results.add(f.join());
        }
        return results;
    }

    private String fetchAndClean(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (compatible; StudentResearchBot/1.0)")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // TEMPORARY DIAGNOSTIC
            System.out.println("DEBUG " + url + " -> status " + response.statusCode()
                    + ", content-type: " + response.headers().firstValue("content-type").orElse("unknown")
                    + ", raw length: " + response.body().length());

            Document doc = Jsoup.parse(response.body());
            doc.select("script, style, nav, footer, header").remove();

            return doc.body().text();

        } catch (Exception e) {
            return "FAILED: " + e.getMessage();
        }
    }
}