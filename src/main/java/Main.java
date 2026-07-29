import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) {
        String apiKey = System.getenv("TAVILY_API_KEY");

        if (apiKey == null) {
            System.err.println("TAVILY_API_KEY environment variable not set.");
            return;
        }

        SerpFetcher serpFetcher = new SerpFetcher(apiKey);
        List<String> urls = serpFetcher.getResultUrls("deep learning models journal papers", 5);

        System.out.println("Got " + urls.size() + " URLs:");
        for (String url : urls) {
            System.out.println(" - " + url);
        }

        ExecutorService pool = Executors.newFixedThreadPool(urls.size());
        PaperFetcher fetcher = new PaperFetcher(pool);

        long startTime = System.currentTimeMillis();
        List<String> results = fetcher.fetchAll(urls);
        long endTime = System.currentTimeMillis();

        System.out.println("Total time: " + (endTime - startTime) + " ms");
        System.out.println("Fetched " + results.size() + " pages.");

        pool.shutdown();
    }
}