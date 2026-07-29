import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ResultAggregator {

    // Thread-safe map: multiple threads can call increment() on this
    // at the same time without corrupting counts.
    private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();

    /**
     * Called by any thread, any number of times, safely.
     * Increments the count for a given feature/heading name.
     */
    public void increment(String item) {
        counts.computeIfAbsent(item, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    /**
     * Convenience: register a whole list of items found in one paper.
     */
    public void addAll(List<String> items) {
        for (String item : items) {
            increment(item);
        }
    }

    /**
     * Returns a plain snapshot map (item -> count) for reporting/charting.
     */
    public Map<String, Integer> getCounts() {
        Map<String, Integer> snapshot = new java.util.HashMap<>();
        for (Map.Entry<String, AtomicInteger> entry : counts.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().get());
        }
        return snapshot;
    }
}