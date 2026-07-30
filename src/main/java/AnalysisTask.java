import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnalysisTask implements Callable<Map<String, Integer>> {

    public enum Mode { CRIME_FEATURES, DL_HEADINGS }

    private final String name;
    private final String searchQuery;
    private final int maxResults;
    private final Mode mode;
    private final String tavilyApiKey;

    public AnalysisTask(String name, String searchQuery, int maxResults,
                        Mode mode, String tavilyApiKey) {
        this.name = name;
        this.searchQuery = searchQuery;
        this.maxResults = maxResults;
        this.mode = mode;
        this.tavilyApiKey = tavilyApiKey;
    }

    public String getName() {
        return name;
    }

    @Override
    public Map<String, Integer> call() {
        System.out.println("[" + name + "] searching: \"" + searchQuery + "\"");

        SerpFetcher serpFetcher = new SerpFetcher(tavilyApiKey);
        List<String> urls = serpFetcher.getResultUrls(searchQuery, maxResults);
        System.out.println("[" + name + "] got " + urls.size() + " URLs");

        if (urls.isEmpty()) {
            return Map.of();
        }

        ExecutorService fetchPool = Executors.newFixedThreadPool(urls.size());
        List<PageResult> pageResults;
        try {
            pageResults = new PaperFetcher(fetchPool).fetchAll(urls);
        } finally {
            fetchPool.shutdown();
        }

        List<PageResult> usablePages = pageResults.stream()
                .filter(PageResult::hasUsefulContent)
                .toList();
        System.out.println("[" + name + "] " + usablePages.size() + "/" + pageResults.size()
                + " pages usable after fetch");

        if (usablePages.isEmpty()) {
            return Map.of();
        }

        ResultAggregator aggregator = new ResultAggregator();

        if (mode == Mode.DL_HEADINGS) {
            // Headings were already extracted structurally in PaperFetcher -
            // just aggregate them directly, no algorithm needed here.
            for (PageResult page : usablePages) {
                aggregator.addAll(page.headings());
            }
        } else {
            // CRIME_FEATURES: TF-IDF needs ALL documents together (for IDF),
            // so this is one batch call, not a per-page loop.
            List<String> bodies = usablePages.stream().map(PageResult::text).toList();
            TfIdfExtractor extractor = new TfIdfExtractor();
            List<List<String>> topTermsPerDoc = extractor.extractTopTermsPerDocument(bodies);

            for (List<String> terms : topTermsPerDoc) {
                aggregator.addAll(terms);
            }
        }

        Map<String, Integer> counts = aggregator.getCounts();
        System.out.println("[" + name + "] extracted " + counts.size() + " distinct items");
        return counts;
    }
}