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

    private final HttpClient client = HttpClient.newHttpClient();
    private final ExecutorService pool;

    public PaperFetcher(ExecutorService pool) {
        this.pool = pool;
    }

    public List<PageResult> fetchAll(List<String> urls) {
        List<CompletableFuture<PageResult>> futures = new ArrayList<>();

        for (String url : urls) {
            CompletableFuture<PageResult> future = CompletableFuture.supplyAsync(() -> {
                long start = System.currentTimeMillis();
                String cleanText = fetchAndClean(url);
                long elapsed = System.currentTimeMillis() - start;

                boolean success = !cleanText.startsWith("FAILED:");
                PageResult result = new PageResult(url, cleanText, success, elapsed);

                String status = result.hasUsefulContent() ? "OK" : "THIN/EMPTY";
                System.out.println("[" + status + "] " + url + " (" + cleanText.length() + " chars, "
                        + elapsed + " ms) [thread: " + Thread.currentThread().getName() + "]");

                return result;
            }, pool);

            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<PageResult> results = new ArrayList<>();
        for (CompletableFuture<PageResult> f : futures) {
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

            Document doc = Jsoup.parse(response.body());
            doc.select("script, style, nav, footer, header").remove();

            return doc.body().text();

        } catch (Exception e) {
            return "FAILED: " + e.getMessage();
        }
    }
}