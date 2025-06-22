package com.samchika.utility;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Helper class to track thread operation timing statistics
 **/
public class ThreadStats {
    private final Map<String, Long> operationStartTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> totalTimeByOperation = new ConcurrentHashMap<>();
    private final Map<String, Integer> operationCounts = new ConcurrentHashMap<>();

    public void startOperation(String name) {
        operationStartTimes.put(name, System.nanoTime());
    }

    public void endOperation(String name) {
        Long startTime = operationStartTimes.remove(name);
        if (startTime != null) {
            long duration = System.nanoTime() - startTime;
            totalTimeByOperation.compute(name, (k, v) -> (v == null) ? duration : v + duration);
            operationCounts.compute(name, (k, v) -> (v == null) ? 1 : v + 1);
        }
    }

    public void printStats() {
        totalTimeByOperation.forEach((operation, totalTime) -> {
            int count = operationCounts.getOrDefault(operation, 0);
            double avgTimeMs = count > 0 ? (totalTime / 1_000_000.0) / count : 0;
            double totalTimeMs = totalTime / 1_000_000.0;
            System.out.printf("  %-20s: %6d calls, %10.2f ms total, %8.3f ms avg%n",
                    operation, count, totalTimeMs, avgTimeMs);
        });
    }

    public Map<String, Long> getOperationStartTimes() {
        return operationStartTimes;
    }

    public Map<String, Long> getTotalTimeByOperation() {
        return totalTimeByOperation;
    }

    public Map<String, Integer> getOperationCounts() {
        return operationCounts;
    }
}
