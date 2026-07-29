import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ResultAggregator {

    private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();

    public void increment(String item) {
        counts.computeIfAbsent(item, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    public void addAll(List<String> items) {
        for (String item : items) {
            increment(item);
        }
    }

    public Map<String, Integer> getCounts() {
        Map<String, Integer> snapshot = new java.util.HashMap<>();
        for (Map.Entry<String, AtomicInteger> entry : counts.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().get());
        }
        return snapshot;
    }
}