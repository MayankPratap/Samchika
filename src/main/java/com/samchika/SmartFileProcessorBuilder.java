package com.samchika;

import java.nio.file.Path;
import java.nio.file.Paths;

public class SmartFileProcessorBuilder {

    private Path inputPath;
    private Path outputPath;
    private int batchSize = 10000;
    private int processorThreads = Runtime.getRuntime().availableProcessors();
    private LineProcessor lineProcessor;
    private BatchProcessor batchProcessor;
    private Path jsonPath;
    private Path csvPath;
    private Boolean isDisplayStats;
    private boolean isOrdered = true;

    public SmartFileProcessorBuilder inputPath(String inputPathString) {

        this.inputPath = Paths.get(inputPathString);
        return this;

    }

    public SmartFileProcessorBuilder outputPath(String outputPathString) {

        this.outputPath = Paths.get(outputPathString);
        return this;
    }

    public SmartFileProcessorBuilder batchSize(int batchSize) {

        this.batchSize = batchSize;
        return this;

    }

    public SmartFileProcessorBuilder processorThreads(int threads) {

        this.processorThreads = threads;
        return this;

    }

    public SmartFileProcessorBuilder lineProcessor(LineProcessor lineProcessor) {

        this.lineProcessor = lineProcessor;
        return this;
    }

    public SmartFileProcessorBuilder batchProcessor(BatchProcessor batchProcessor) {

        this.batchProcessor = batchProcessor;
        return this;

    }

    public SmartFileProcessorBuilder outputToJson(String jsonPathString){
        this.jsonPath = Paths.get(jsonPathString);
        return this;
    }

    public SmartFileProcessorBuilder outputToCSV(String csvPathString){
        this.csvPath = Paths.get(csvPathString);
        return this;
    }

    public SmartFileProcessorBuilder displayStats(Boolean isDisplayStats){
        this.isDisplayStats = isDisplayStats;
        return this;
    }

    public SmartFileProcessorBuilder ordered(boolean isOrdered){

        this.isOrdered = isOrdered;
        return this;

    }

    public SmartFileProcessor build() {

        // validation
        if (inputPath == null || outputPath == null) {

            throw new IllegalArgumentException("Input and output paths must be provided");

        }

        if (lineProcessor == null && batchProcessor == null) {

            throw new IllegalArgumentException("Either line processor or batch processor must be provided");

        }

        return new SmartFileProcessor(this);

    }

    // Package-private getters for SmartFileProcessor to access builder fields
    Path getInputPath() { return inputPath; }
    Path getOutputPath() { return outputPath; }
    int getBatchSize() { return batchSize; }
    int getProcessorThreads() { return processorThreads; }
    LineProcessor getLineProcessor() { return lineProcessor; }
    BatchProcessor getBatchProcessor() { return batchProcessor; }
    Path getJsonPath() { return jsonPath; }
    Path getCsvPath() { return csvPath; }
    Boolean getIsDisplayStats() { return isDisplayStats; }
    boolean getIsOrdered() { return isOrdered; }

}
