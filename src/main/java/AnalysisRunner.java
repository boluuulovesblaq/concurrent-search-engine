import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AnalysisRunner {

    public Map<String, AnalysisResult> runAll(List<AnalysisTask> tasks) {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());

        Map<String, Future<AnalysisResult>> futures = new HashMap<>();
        for (AnalysisTask task : tasks) {
            futures.put(task.getName(), pool.submit(task));
        }

        Map<String, AnalysisResult> resultsByAnalysis = new HashMap<>();
        for (Map.Entry<String, Future<AnalysisResult>> entry : futures.entrySet()) {
            String taskName = entry.getKey();
            try {
                resultsByAnalysis.put(taskName, entry.getValue().get());
            } catch (Exception e) {
                System.out.println("[" + taskName + "] failed: " + e.getMessage());
                resultsByAnalysis.put(taskName, new AnalysisResult(Map.of(), List.of()));
            }
        }

        pool.shutdown();
        return resultsByAnalysis;
    }
}