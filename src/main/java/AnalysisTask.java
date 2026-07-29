import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One full analysis pipeline: search -> fetch papers -> extract items
 * (features or headings) -> aggregate counts. Generic over SemanticExtractor.Mode
 * so the same class runs both required analyses (crime features, DL headings).
 *
 * Concurrency: fetching is already parallel (PaperFetcher). Extraction is ALSO
 * run one-thread-per-paper here, with every thread writing into the same
 * ResultAggregator concurrently -- that's the shared-state concurrency point.
 */
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

        // Fetch stage: concurrent, one thread per URL (existing PaperFetcher behavior).
        ExecutorService fetchPool = Executors.newFixedThreadPool(urls.size());
        List<String> texts;
        try {
            texts = new PaperFetcher(fetchPool).fetchAll(urls);
        } finally {
            fetchPool.shutdown();
        }

        // Drop anything that failed to fetch or is too thin to be useful.
        List<String> usableTexts = texts.stream()
                .filter(t -> t != null && !t.startsWith("FAILED:") && t.length() >= 100)
                .toList();
        System.out.println("[" + name + "] " + usableTexts.size() + "/" + texts.size()
                + " pages usable after fetch");

        // Extraction stage: concurrent, one thread per paper. Every thread calls
        // aggregator.addAll(...) on the SAME aggregator -- this is the genuine
        // shared-state race the ResultAggregator's ConcurrentHashMap/AtomicInteger protects.
        ResultAggregator aggregator = new ResultAggregator();
        SemanticExtractor extractor = new SemanticExtractor(anthropicApiKey);
        ExecutorService extractPool = Executors.newFixedThreadPool(Math.min(usableTexts.size(), 8));

        try {
            List<CompletableFuture<Void>> futures = usableTexts.stream()
                    .map(text -> CompletableFuture.runAsync(() -> {
                        List<String> items = extractor.extract(text, mode);
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