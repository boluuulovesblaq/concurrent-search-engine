import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnalysisTask implements Callable<Map<String, Integer>> {

    private final String name;
    private final String searchQuery;
    private final int maxResults;
    private final SemanticExtractor.Mode mode;
    private final String tavilyApiKey;
    private final String anthropicApiKey;

    public AnalysisTask(String name, String searchQuery, int maxResults,
                        SemanticExtractor.Mode mode,
                        String tavilyApiKey, String anthropicApiKey) {
        this.name = name;
        this.searchQuery = searchQuery;
        this.maxResults = maxResults;
        this.mode = mode;
        this.tavilyApiKey = tavilyApiKey;
        this.anthropicApiKey = anthropicApiKey;
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
        SemanticExtractor extractor = new SemanticExtractor(anthropicApiKey, true); // mock mode - no real API calls
        ExecutorService extractPool = Executors.newFixedThreadPool(Math.min(usablePages.size(), 8));

        try {
            List<CompletableFuture<Void>> futures = usablePages.stream()
                    .map(page -> CompletableFuture.runAsync(() -> {
                        List<String> items = extractor.extract(page.text(), mode);
                        aggregator.addAll(items);
                    }, extractPool))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            extractPool.shutdown();
        }

        Map<String, Integer> counts = aggregator.getCounts();
        System.out.println("[" + name + "] extracted " + counts.size() + " distinct items");
        return counts;
    }
}