import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Runs multiple AnalysisTasks CONCURRENTLY against a shared pool -- e.g. the
 * "crime features" and "DL sub-headings" analyses proceed at the same time
 * instead of one finishing before the next starts.
 */
public class AnalysisRunner {

    private final ExecutorService pool;

    public AnalysisRunner(ExecutorService pool) {
        this.pool = pool;
    }

    /**
     * Submits every task at once, then blocks until all are done.
     * Returns a name -> (item -> count) map, one entry per task.
     */
    public Map<String, Map<String, Integer>> runAll(List<AnalysisTask> tasks) {
        Map<String, Future<Map<String, Integer>>> futures = new LinkedHashMap<>();
        for (AnalysisTask task : tasks) {
            futures.put(task.getName(), pool.submit(task));
        }

        Map<String, Map<String, Integer>> results = new LinkedHashMap<>();
        for (Map.Entry<String, Future<Map<String, Integer>>> entry : futures.entrySet()) {
            try {
                results.put(entry.getKey(), entry.getValue().get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while waiting on analysis: " + entry.getKey());
                results.put(entry.getKey(), Map.of());
            } catch (ExecutionException e) {
                System.err.println("Analysis \"" + entry.getKey() + "\" failed: " + e.getCause());
                results.put(entry.getKey(), Map.of());
            }
        }

        return results;
    }
}