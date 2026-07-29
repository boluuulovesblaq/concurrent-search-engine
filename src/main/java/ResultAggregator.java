import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ResultAggregator {

    private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();

    public void addAll(List<String> items) {
        for (String item : items) {
            if (item == null || item.isBlank()) continue;
            String normalized = normalize(item);

            counts.computeIfAbsent(normalized, k -> new AtomicInteger(0))
                    .incrementAndGet();
        }
    }

    private String normalize(String item) {
        return item.trim().toLowerCase();
    }

    public Map<String, Integer> getCounts() {
        return counts.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get()
                ));
    }
}