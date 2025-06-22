package com.samchika;

import com.samchika.utility.ThreadStats;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SmartFileProcessor {

    // Required fields
    private final Path inputPath;
    private final Path outputPath;

    // Optional / configurable
    private final int batchSize;
    private final int processorThreads;
    private final LineProcessor lineProcessor;
    private final BatchProcessor batchProcessor;
    private final Path jsonPath;
    private final Path csvPath;
    private final Boolean isDisplayStats;
    private final boolean isOrdered;

    // not part of builder
    private static final int QUEUE_CAPACITY = 100;
    private static final List<String> POISON_PILL = Collections.emptyList();

    // Counters for profiling and monitoring
    private static final AtomicInteger batchesSubmitted = new AtomicInteger(0);
    private static final AtomicInteger batchesProcessed = new AtomicInteger(0);
    private static final AtomicInteger batchesWritten = new AtomicInteger(0);
    private static final AtomicInteger flushesExecuted = new AtomicInteger(0);

    // flag to check if the process has already been executed.
    private final AtomicBoolean hasRun = new AtomicBoolean(false);


    private static long wallClockStart;
    private static long wallClockEnd;

    private static long memoryStart;
    private static long memoryEnd;

    // Thread-specific timing data
    private static final ConcurrentHashMap<String, ThreadStats> threadStatsMap = new ConcurrentHashMap<>();


    // package-private constructor : builder can access it from outside.
    SmartFileProcessor(SmartFileProcessorBuilder builder) {

        this.inputPath = builder.getInputPath();
        this.outputPath = builder.getOutputPath();
        this.batchSize = builder.getBatchSize();
        this.processorThreads = builder.getProcessorThreads();
        this.lineProcessor = builder.getLineProcessor();
        this.batchProcessor = builder.getBatchProcessor();
        this.jsonPath = builder.getJsonPath();
        this.csvPath = builder.getCsvPath();
        this.isDisplayStats = builder.getIsDisplayStats();
        this.isOrdered = builder.getIsOrdered();
    }

    public static SmartFileProcessorBuilder builder(){

        return new SmartFileProcessorBuilder();

    }

    public void execute(){

        if (!hasRun.compareAndSet(false, true)) {
            throw new IllegalStateException("One smart-file-processor instance is already running.");
        }

        wallClockStart = System.nanoTime(); // Start wall-clock timer
        memoryStart = getUsedMemory();

        // Named thread factories for better profiler identification
        ThreadFactory processorFactory = r -> {
            Thread t = new Thread(r, "processor-thread-" + threadStatsMap.size());
            threadStatsMap.put(t.getName(), new ThreadStats());
            return t;
        };

        ThreadFactory flushFactory = r -> {
            Thread t = new Thread(r, "flush-thread");
            threadStatsMap.put(t.getName(), new ThreadStats());
            return t;
        };

        // Shared queue between producers ( processors ) and the writer.
        BlockingQueue<List<String>> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        ExecutorService processorPool = new ThreadPoolExecutor(
                this.processorThreads,
                this.processorThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(20), // limit task queue size
                processorFactory,
                new ThreadPoolExecutor.CallerRunsPolicy() // apply backpressure
        );

        ExecutorService flushExecutor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(10),
                flushFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // Dedicated writer thread
        Thread writerThread = new Thread(() -> {

            ThreadStats stats = new ThreadStats();
            threadStatsMap.put("writer-thread", stats);

            try(BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)){

                List<String> buffer = new ArrayList<>();
                int bufferSize = 0;

                final int FLUSH_THRESHOLD_BYTES = 8 * 1024 * 1024; // 8 MB


                while(true){
                    stats.startOperation("queue-take");
                    List<String> processedBatch = queue.take();
                    stats.endOperation("queue-take");
                    if(processedBatch == POISON_PILL) break;

                    stats.startOperation("buffer-add");
                    for(String line : processedBatch){

                        buffer.add(line);
                        bufferSize += line.length()*2;
                    }
                    stats.endOperation("buffer-add");

                    batchesWritten.incrementAndGet();
                    if(bufferSize >= FLUSH_THRESHOLD_BYTES){

                        List<String> bufferToFlush = new ArrayList<>(buffer);
                        buffer.clear();
                        bufferSize = 0;

                        flushExecutor.submit(() -> {
                            threadStatsMap.putIfAbsent(Thread.currentThread().getName(), new ThreadStats());
                            ThreadStats flushStats = threadStatsMap.get(Thread.currentThread().getName());
                            flushStats.startOperation("flush");
                            try {
                                flushBuffer(bufferToFlush, writer);
                                flushesExecuted.incrementAndGet();
                            } catch (IOException e) {
                                System.err.println("Flush error: " + e.getMessage());
                            }

                            flushStats.endOperation("flush");

                        });
                    }

                    //System.out.println("Writer thread: after processing time : " + System.currentTimeMillis());

                }

                if(!buffer.isEmpty()){
                    stats.startOperation("final-flush");
                    flushBuffer(buffer, writer);
                    stats.endOperation("final-flush");
                    flushesExecuted.incrementAndGet();
                }

                flushExecutor.shutdown();
                flushExecutor.awaitTermination(1, TimeUnit.HOURS);

            } catch (IOException | InterruptedException e) {
                System.err.println("Error writing to file: " + e.getMessage());
                Thread.currentThread().interrupt();
            }

        }, "writer-thread");

        writerThread.start();

        // Read, batch and submit processing tasks.
        try(BufferedReader reader = Files.newBufferedReader(inputPath)){
            List<String> batch = new ArrayList<>(batchSize);
            String line;

            ThreadStats mainStats = new ThreadStats();
            threadStatsMap.put("main-thread", mainStats);

            while((line = reader.readLine())!=null){
                batch.add(line);
                if(batch.size() == batchSize){
                    // submit a copy of batch for processing
                    List<String> safeBatch = new ArrayList<>(batch); // copy before clearing.
                    mainStats.startOperation("submit-task");
                    Runnable task = () -> {
                        threadStatsMap.putIfAbsent(Thread.currentThread().getName(), new ThreadStats());
                        ThreadStats stats = threadStatsMap.get(Thread.currentThread().getName());
                        stats.startOperation("process-batch");
                        List<String> processed = processBatch(safeBatch);
                        stats.endOperation("process-batch");
                        batchesProcessed.incrementAndGet();

                        stats.startOperation("queue-put");
                        try {
                            queue.put(processed);
                        } catch (InterruptedException ie) {
                            System.err.println("Error while processing file: " + ie.getMessage());
                            Thread.currentThread().interrupt();
                        }
                        stats.endOperation("queue-put");
                    };
                    processorPool.execute(task);
                    mainStats.endOperation("submit-task");
                    batchesSubmitted.incrementAndGet();
                    batch.clear();
                }
            }

            // submit any remaining lines
            if(!batch.isEmpty()){
                List<String> safeBatch = new ArrayList<>(batch);
                mainStats.startOperation("submit-final-batch");
                Runnable task = () -> {
                    threadStatsMap.putIfAbsent(Thread.currentThread().getName(), new ThreadStats());
                    ThreadStats stats = threadStatsMap.get(Thread.currentThread().getName());
                    stats.startOperation("process-batch");
                    List<String> processed = processBatch(safeBatch);
                    stats.endOperation("process-batch");
                    batchesProcessed.incrementAndGet();

                    stats.startOperation("queue-put");
                    try {
                        queue.put(processed);
                    } catch (InterruptedException ie) {
                        System.err.println("Error while processing file: " + ie.getMessage());
                        Thread.currentThread().interrupt();
                    }
                    stats.endOperation("queue-put");
                };
                processorPool.execute(task);
                mainStats.endOperation("submit-final-batch");
                batchesSubmitted.incrementAndGet();

            }


        }catch(IOException e){
            System.err.println("Error during file procesing: "+ e.getMessage());
        }

        // shutdown processor pool and wait for tasks to finish
        processorPool.shutdown();
        try{
            processorPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            System.err.println("Error waiting for processor pool to terminate: " + e.getMessage());
            Thread.currentThread().interrupt();
        }

        // Signal writer to finish and wait
        try{
            queue.put(POISON_PILL);
            writerThread.join();
        }catch (InterruptedException ie){
            System.err.println("Error waiting for writer thread to finish: " + ie.getMessage());
            Thread.currentThread().interrupt();
        }

        wallClockEnd = System.nanoTime(); // End wall-clock timer
        memoryEnd = getUsedMemory();

        // Depending upon user preferences we print stats, export to json, or export to csv.
        if(isDisplayStats!=null && isDisplayStats.booleanValue()==true ){
            displayStats();
        }

        if(jsonPath!=null){
            exportStatsToJSON(jsonPath);
        }

        if(csvPath!=null){
            exportStatsToCSV(csvPath);
        }


    }

    private List<String> processBatch(List<String> batch) {

        if(batchProcessor != null){
            return batchProcessor.processBatch(batch);
        }else{

            List<String> processedBatch = new ArrayList<>(batch.size());
            for(String line : batch){
                processedBatch.add(lineProcessor.processLine(line));
            }
            return processedBatch;

        }

    }

    private void flushBuffer(List<String> buffer, BufferedWriter writer) throws IOException{
        for(String line : buffer){
            writer.write(line);
            writer.newLine();
        }
    }

    private void displayStats(){

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

    private void exportStatsToJSON(Path jsonPath) {

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

    private static long getCurrentPID() {
        return ProcessHandle.current().pid();
    }

    private void exportStatsToCSV(Path csvPath){

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



    private static long getUsedMemory(){
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();

    }



}
