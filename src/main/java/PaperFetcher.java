import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
 *   - blocked/paywall/error pages (their boilerplate isn't real content)
 *   - thin pages (too little content to be a real usable source)
 *
 * PDFs are no longer rejected outright: real text is extracted from them
 * with Apache PDFBox instead of skipping them (they used to come back as
 * near-empty bodies since Jsoup can't parse binary PDF bytes as HTML).
 * PDFs have no headings extracted - PDFBox gives us a flat text stream,
 * not a tagged document structure, so heading detection isn't reliable
 * for this format. A PDF can still contribute to CRIME_FEATURES text
 * scoring; it just won't contribute DL_HEADINGS sub-headings.
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

    // Heading text matching any of these isn't a real section/sub-heading -
    // it's site chrome, citation-tracker boilerplate, or nav copy that
    // happens to sit inside an h1-h4 tag. Reject before it's counted.
    private static final List<String> JUNK_HEADING_PHRASES = List.of(
            "cited by", "cited by other articles", "related articles",
            "similar articles", "recommenders", "recommended articles",
            "permalink", "share this", "download citation", "export citation",
            "sign in", "log in", "create account", "subscribe", "subscription",
            "cookie", "privacy policy", "terms of use", "terms of service",
            "table of contents", "back to top", "skip to content", "skip to main",
            "search", "menu", "navigation", "advertisement", "sponsored",
            "you may also like", "more from this", "further reading"
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

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] bodyBytes = response.body();

            String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase();
            boolean looksLikePdf = contentType.contains("application/pdf")
                    || url.toLowerCase().endsWith(".pdf");

            if (looksLikePdf) {
                return extractFromPdf(url, bodyBytes);
            }

            // Reject other non-HTML content (images, zips, etc.) - only
            // PDFs get a dedicated extraction path; everything else that
            // isn't text/html is still garbage-in-garbage-out for Jsoup.
            if (!contentType.contains("text/html") && !contentType.isEmpty()) {
                return failed(url, "unsupported content-type: " + contentType);
            }

            String html = new String(bodyBytes, StandardCharsets.UTF_8);
            return extractFromHtml(url, html);

        } catch (Exception e) {
            return failed(url, e.getMessage());
        }
    }

    private PageResult extractFromHtml(String url, String html) {
        Document doc = Jsoup.parse(html);

        Elements headingElements = doc.select("h1, h2, h3, h4");
        List<String> headings = new ArrayList<>();
        for (Element h : headingElements) {
            String text = normalizeHeading(h.text());
            if (!text.isEmpty() && text.length() < MAX_HEADING_LENGTH && !isJunkHeading(text)) {
                headings.add(text);
            }
        }

        doc.select("script, style, nav, footer, header").remove();
        String cleanText = doc.body().text();

        return validateAndBuild(url, cleanText, headings);
    }

    private PageResult extractFromPdf(String url, byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String rawText = stripper.getText(document);

            // Collapse the excessive whitespace/line-break noise PDF text
            // extraction typically produces (columns, page breaks, etc.)
            // into normal prose so it scores fairly against HTML text.
            String cleanText = rawText.replaceAll("\\s+", " ").trim();

            // No headings from PDFs - see class javadoc. Empty list means
            // a PDF can still feed CRIME_FEATURES text scoring but won't
            // contribute to DL_HEADINGS sub-heading counts.
            return validateAndBuild(url, cleanText, List.of());

        } catch (Exception e) {
            return failed(url, "PDF extraction failed: " + e.getMessage());
        }
    }

    private PageResult validateAndBuild(String url, String cleanText, List<String> headings) {
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
    }

    private PageResult failed(String url, String reason) {
        return new PageResult(url, "FAILED: " + reason, List.of(), false, 0L);
    }

    private boolean isJunkHeading(String normalizedText) {
        for (String junk : JUNK_HEADING_PHRASES) {
            if (normalizedText.contains(junk)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeHeading(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase()
                .replaceAll("^(section\\s*)?[0-9ivxIVX]+[.):]?\\s*", "")
                .replaceAll("[.:]+$", "")
                .trim();
    }
}