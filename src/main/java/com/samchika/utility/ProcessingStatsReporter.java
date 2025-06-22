package com.samchika.utility;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ProcessingStatsReporter {

    private final ConcurrentHashMap<String, ThreadStats> threadStatsMap;
    private final AtomicInteger batchesSubmitted;
    private final AtomicInteger batchesProcessed;
    private final AtomicInteger batchesWritten;
    private final AtomicInteger flushesExecuted;
    private final long wallClockStart;
    private final long wallClockEnd;
    private final long memoryStart;
    private final long memoryEnd;

    public ProcessingStatsReporter(ConcurrentHashMap<String, ThreadStats> threadStatsMap,
                                   AtomicInteger batchesSubmitted,
                                   AtomicInteger batchesProcessed,
                                   AtomicInteger batchesWritten,
                                   AtomicInteger flushesExecuted,
                                   long wallClockStart,
                                   long wallClockEnd,
                                   long memoryStart,
                                   long memoryEnd) {
        this.threadStatsMap = threadStatsMap;
        this.batchesSubmitted = batchesSubmitted;
        this.batchesProcessed = batchesProcessed;
        this.batchesWritten = batchesWritten;
        this.flushesExecuted = flushesExecuted;
        this.wallClockStart = wallClockStart;
        this.wallClockEnd = wallClockEnd;
        this.memoryStart = memoryStart;
        this.memoryEnd = memoryEnd;
    }

    /**
     * Displays processing statistics to the console.
     */
    public void displayStats(){

        // Print thread statistics
        System.out.println("\n=== Thread Performance Statistics ===");

        threadStatsMap.forEach((threadName, threadStats) -> {
            System.out.println("\nThread: " + threadName);
            threadStats.printStats();
        });


        System.out.println("Batches submitted: " + batchesSubmitted.get());
        System.out.println("Batches processed: " + batchesProcessed.get());
        System.out.println("Batches written: " + batchesWritten.get());
        System.out.println("Flushes executed: " + flushesExecuted.get());

        long usedMemMB = (memoryEnd - memoryStart) / (1024 * 1024);
        System.out.println("Memory Used       : " + usedMemMB + " MB");
        System.out.println("=======================================\n");

        long wallClockTime = wallClockEnd - wallClockStart;
        double wallClockMilliSecs = wallClockTime / 1_000_000.0;
        System.out.println("Wall-clock time   : " + String.format("%.2f", wallClockMilliSecs) + " millisecs");

    }

    /**
     * Exports processing statistics to a JSON file.
     *
     * @param jsonPath The path where the JSON file should be written
     */
    public void exportStatsToJSON(Path jsonPath) {

        System.out.println("Stats JSON will be written to: " + jsonPath);


        try (BufferedWriter writer = Files.newBufferedWriter(jsonPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write("{\n");

            // Write overall stats
            writer.write("  \"summary\": {\n");
            writer.write("    \"batchesSubmitted\": " + batchesSubmitted.get() + ",\n");
            writer.write("    \"batchesProcessed\": " + batchesProcessed.get() + ",\n");
            writer.write("    \"batchesWritten\": " + batchesWritten.get() + ",\n");
            writer.write("    \"flushesExecuted\": " + flushesExecuted.get() + ",\n");
            writer.write("    \"memoryUsedMB\": " + ( (memoryEnd - memoryStart)  / (1024 * 1024)) + ",\n");
            writer.write("    \"WallClockTimeInMs\": " + (wallClockEnd - wallClockStart)/ 1_000_000.0 + "\n");
            writer.write("  },\n");

            // Write thread-specific stats
            writer.write("  \"threads\": [\n");

            int threadCount = threadStatsMap.size();
            int i = 0;
            for (Map.Entry<String, ThreadStats> entry : threadStatsMap.entrySet()) {
                String threadName = entry.getKey();
                ThreadStats stats = entry.getValue();

                writer.write("    {\n");
                writer.write("      \"thread\": \"" + threadName + "\",\n");
                writer.write("      \"operations\": [\n");

                int opCount = stats.getTotalTimeByOperation().size();
                int j = 0;
                for (Map.Entry<String, Long> op : stats.getTotalTimeByOperation().entrySet()) {
                    String opName = op.getKey();
                    long totalTime = op.getValue();
                    int count = stats.getOperationCounts().getOrDefault(opName, 0);
                    double avgTime = count > 0 ? (totalTime / 1_000_000.0) / count : 0;
                    double totalTimeMs = totalTime / 1_000_000.0;

                    writer.write("        {\n");
                    writer.write("          \"operation\": \"" + opName + "\",\n");
                    writer.write("          \"calls\": " + count + ",\n");
                    writer.write("          \"totalMs\": " + String.format("%.2f", totalTimeMs) + ",\n");
                    writer.write("          \"avgMs\": " + String.format("%.3f", avgTime) + "\n");
                    writer.write("        }" + (j < opCount - 1 ? "," : "") + "\n");
                    j++;
                }

                writer.write("      ]\n");
                writer.write("    }" + (i < threadCount - 1 ? "," : "") + "\n");
                i++;
            }

            writer.write("  ]\n");
            writer.write("}\n");

        } catch (IOException e) {
            System.err.println("Failed to export stats to JSON: " + e.getMessage());
        }

    }

    /**
     * Exports processing statistics to a CSV file.
     *
     * @param csvPath The path where the CSV file should be written
     */
    public void exportStatsToCSV(Path csvPath){

        System.out.println("Stats CSV will be written to: " + csvPath);

        try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            // Write header
            writer.write("# Summary\n");
            writer.write("# Batches Submitted, Batches Processed, Batches Written, Flushes Executed, Memory Used (MB), Wall clock time In Ms\n");
            writer.write(String.format("# %d, %d, %d, %d, %d, %.2f\n\n",
                    batchesSubmitted.get(),
                    batchesProcessed.get(),
                    batchesWritten.get(),
                    flushesExecuted.get(),
                    (memoryEnd - memoryStart)  / (1024 * 1024),
                    (wallClockEnd - wallClockStart)/ 1_000_000.0));

            // Write column headers
            writer.write("Thread,Operation,Calls,Total Time (ms),Average Time (ms)\n");

            for (Map.Entry<String, ThreadStats> entry : threadStatsMap.entrySet()) {
                String threadName = entry.getKey();
                ThreadStats stats = entry.getValue();

                for (Map.Entry<String, Long> op : stats.getTotalTimeByOperation().entrySet()) {
                    String operation = op.getKey();
                    long totalTimeNs = op.getValue();
                    int calls = stats.getOperationCounts().getOrDefault(operation, 0);
                    double totalTimeMs = totalTimeNs / 1_000_000.0;
                    double avgTimeMs = calls > 0 ? totalTimeMs / calls : 0;

                    writer.write(String.format(
                            "\"%s\",\"%s\",%d,%.2f,%.3f\n",
                            threadName, operation, calls, totalTimeMs, avgTimeMs));
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to export stats to CSV: " + e.getMessage());
        }

    }



}
