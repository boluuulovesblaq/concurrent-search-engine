import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class SafeCountDemo {
    public static void main(String[] args) throws InterruptedException {

        ConcurrentHashMap<String, AtomicInteger> safeCounts = new ConcurrentHashMap<>();
        safeCounts.put("featureX", new AtomicInteger(0));

        int numThreads = 100;
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    safeCounts.get("featureX").incrementAndGet();
                }
                latch.countDown();
            }).start();
        }

        latch.await();

        System.out.println("Expected count: " + (numThreads * 1000));
        System.out.println("Actual count:   " + safeCounts.get("featureX").get());
    }
}