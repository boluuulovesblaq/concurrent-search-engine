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
 *   - the list of heading tag contents, h1-h4, normalized and de-numbered
 *     (for sub-heading counting)
 *
 * Filters out pages that would poison downstream results:
 *   - PDFs (Jsoup can't parse PDF binary; garbage in, garbage out)
 *   - blocked/paywall/error pages (their boilerplate isn't real content)
 *   - thin pages (too little content to be a real usable source)
 */
public class PaperFetcher {

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ExecutorService pool;

    private static final int MIN_USABLE_LENGTH = 200;
    private static final int MAX_HEADING_LENGTH = 60;

    // Boilerplate phrases indicating we got a blocked/error/paywall page
    // instead of real article content - these must not become "features"
    // or "headings" in the aggregated results.
    private static final List<String> BLOCK_INDICATORS = List.of(
            "access denied", "access blocked", "debug information",
            "there was a problem providing the content",
            "please verify you are a human", "enable javascript",
            "403 forbidden", "page not found", "captcha"
    );

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

                result = new PageResult(result.url(), result.text(), result.headings(),
                        result.success(), elapsed);

                String status = result.hasUsefulContent() ? "OK" : "SKIPPED";
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

            // Reject PDFs and other non-HTML content outright - Jsoup parsing
            // binary PDF bytes produces garbage (PDF structure keywords like
            // "stream"/"endobj"), not real text.
            String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase();
            if (!contentType.contains("text/html") && !contentType.isEmpty()) {
                return failed(url, "unsupported content-type: " + contentType);
            }

            Document doc = Jsoup.parse(response.body());

            Elements headingElements = doc.select("h1, h2, h3, h4");
            List<String> headings = new ArrayList<>();
            for (Element h : headingElements) {
                String text = normalizeHeading(h.text());
                if (!text.isEmpty() && text.length() < MAX_HEADING_LENGTH) {
                    headings.add(text);
                }
            }

            doc.select("script, style, nav, footer, header").remove();
            String cleanText = doc.body().text();

            // Reject blocked/error/paywall pages - their boilerplate text
            // and headings aren't real article content.
            String lowerText = cleanText.toLowerCase();
            for (String indicator : BLOCK_INDICATORS) {
                if (lowerText.contains(indicator)) {
                    return failed(url, "blocked/error page detected");
                }
            }

            if (cleanText.length() < MIN_USABLE_LENGTH) {
                return failed(url, "content too thin");
            }

            return new PageResult(url, cleanText, headings, true, 0L);

        } catch (Exception e) {
            return failed(url, e.getMessage());
        }
    }

    private PageResult failed(String url, String reason) {
        return new PageResult(url, "FAILED: " + reason, List.of(), false, 0L);
    }

    private String normalizeHeading(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase()
                .replaceAll("^(section\\s*)?[0-9ivxIVX]+[.):]?\\s*", "")
                .replaceAll("[.:]+$", "")
                .trim();
    }
}