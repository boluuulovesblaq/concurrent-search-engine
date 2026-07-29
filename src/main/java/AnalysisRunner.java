import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs multiple AnalysisTasks CONCURRENTLY - not just each task's internal
 * fetch/extract steps in parallel, but the tasks themselves running at the
 * same time. Since AnalysisTask implements Callable<Map<String,Integer>>,
 * ExecutorService.submit() gives us a Future per task, and we collect all
 * results once every task is done.
 */
public class AnalysisRunner {

    public Map<String, Map<String, Integer>> runAll(List<AnalysisTask> tasks) {
        // One thread per task - each AnalysisTask internally spins up its
        // own fetch/extract thread pools, so this outer pool just needs to
        // let each full task run without blocking on the others.
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());

        Map<String, Future<Map<String, Integer>>> futures = new HashMap<>();
        for (AnalysisTask task : tasks) {
            futures.put(task.getName(), pool.submit(task));
        }

        Map<String, Map<String, Integer>> resultsByAnalysis = new HashMap<>();
        for (Map.Entry<String, Future<Map<String, Integer>>> entry : futures.entrySet()) {
            String taskName = entry.getKey();
            try {
                Map<String, Integer> counts = entry.getValue().get(); // blocks until this task is done
                resultsByAnalysis.put(taskName, counts);
            } catch (Exception e) {
                System.out.println("[" + taskName + "] failed: " + e.getMessage());
                resultsByAnalysis.put(taskName, Map.of());
            }
        }

        pool.shutdown();
        return resultsByAnalysis;
    }
}