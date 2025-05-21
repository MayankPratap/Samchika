# Samchika Examples
This directory contains example implementations that demonstrate different use cases for the Samchika library.
Available Examples

1. Log Analysis (LogAnalysis.java)
Demonstrates how to process large log files to extract patterns, errors, and statistics.
java

### Highlight from the example
```
SmartFileProcessor.builder()
    .inputPath("server.log")
    .outputPath("errors.log") 
    .lineProcessor(line -> line.contains("ERROR") ? line : null)
    .outputToJson("log_stats.json")
    .build()
    .execute();
```

2. Data Transformation (DataTransformation.java)
Shows how to convert data between formats while maintaining performance.
java

### Highlight from the example
```
SmartFileProcessor.builder()
    .inputPath("source.csv")
    .outputPath("target.json")
    .batchProcessor(batch -> {
        return batch.stream()
            .map(JsonConverter::fromCsv)
            .collect(Collectors.toList());
    })
    .build()
    .execute();
```

3. Text Processing (TextProcessing.java)
Demonstrates NLP and text analysis on large corpora.
java

### Highlight from the example
```
SmartFileProcessor.builder()
    .inputPath("corpus.txt")
    .outputPath("processed.txt")
    .batchSize(5000)
    .processorThreads(16)
    .lineProcessor(TextAnalyzer::extractEntities)
    .build()
    .execute();
```

5. Batch Reporting (BatchReporting.java)
Shows how to generate reports from large datasets with performance metrics.
java

### Highlight from the example

```
SmartFileProcessor.builder()
    .inputPath("monthly_data.csv")
    .outputPath("report.txt")
    .batchProcessor(ReportGenerator::createSections)
    .outputToCSV("report_metrics.csv")
    .build()
    .execute();
```



## Creating Your Own Examples

When creating your own implementations, consider:

- Batch Size: Adjust based on memory availability and record size
- Thread Count: Default is optimal for most cases, but IO-bound vs CPU-bound tasks may benefit from different configurations
- Processing Type: Choose between line processors (simple transformations) and batch processors (complex operations on groups of records)
- Performance Monitoring: Enable statistics for tuning and optimization
