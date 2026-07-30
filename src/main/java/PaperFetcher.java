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
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Fetches URLs concurrently. For each page, extracts BOTH:
 *   - cleaned body text (for TF-IDF feature extraction)
 *   - the list of heading tag contents, h1-h4, in document order (for
 *     sub-heading counting)
 * Both come from the same parsed Document, so we do it once per page
 * rather than fetching twice per task type.
 */
public class PaperFetcher {

    // followRedirects(NORMAL) matters: some journal/paper sites (e.g. PMC,
    // JMLR) respond with 301/302 redirects rather than the page directly.
    // Without this, those pages come back empty even though status looks fine.
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ExecutorService pool;

    public PaperFetcher(ExecutorService pool) {
        this.pool = pool;
    }

    public List<PageResult> fetchAll(List<String> urls) {
        List<CompletableFuture<PageResult>> futures = new ArrayList<>();

        for (String url : urls) {
            CompletableFuture<PageResult> future = CompletableFuture.supplyAsync(() -> {
                long start = System.currentTimeMillis();
                PageResult result = fetchAndExtract(url);
                long elapsed = System.currentTimeMillis() - start;

                // rebuild with actual elapsed time now that fetch is done
                result = new PageResult(result.url(), result.text(), result.headings(),
                        result.success(), elapsed);

                String status = result.hasUsefulContent() ? "OK" : "THIN/EMPTY";
                System.out.println("[" + status + "] " + url + " (" + result.text().length()
                        + " chars, " + result.headings().size() + " headings, " + elapsed
                        + " ms) [thread: " + Thread.currentThread().getName() + "]");

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

    private PageResult fetchAndExtract(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (compatible; StudentResearchBot/1.0)")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Document doc = Jsoup.parse(response.body());

            // Extract headings BEFORE stripping/cleaning, straight from the
            // structural tags - this is direct HTML parsing, not statistical
            // keyword extraction, since a heading is a structural element,
            // not just a frequent phrase in the body.
            Elements headingElements = doc.select("h1, h2, h3, h4");
            List<String> headings = new ArrayList<>();
            for (Element h : headingElements) {
                String text = h.text().trim();
                if (!text.isEmpty()) {
                    headings.add(text);
                }
            }

            // Now clean the document for body text (used by TF-IDF)
            doc.select("script, style, nav, footer, header").remove();
            String cleanText = doc.body().text();

            return new PageResult(url, cleanText, headings, true, 0L);

        } catch (Exception e) {
            return new PageResult(url, "FAILED: " + e.getMessage(), List.of(), false, 0L);
        }
    }
}