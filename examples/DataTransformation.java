import com.samchika.SmartFileProcessor;
import com.samchika.BatchProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Example demonstrating how to use Samchika for data transformation between formats.
 * 
 * This example shows:
 * 1. Batch processing capability for higher throughput
 * 2. Converting CSV records to a different format
 * 3. Performance optimizations for large datasets
 */
public class DataTransformation {

    public static void main(String[] args) {
        // Create a batch processor to handle CSV to JSON conversion
        BatchProcessor csvToJsonConverter = new BatchProcessor() {
            @Override
            public List<String> processBatch(List<String> batch) {
                return batch.stream()
                    .map(DataTransformation::convertCsvLineToJson)
                    .collect(Collectors.toList());
            }
        };
        
        // Configure and run the processor
        SmartFileProcessor.builder()
            .inputPath("data/customers.csv")
            .outputPath("data/customers.json")
            .batchSize(5000)                  // Optimized batch size for this conversion
            .processorThreads(8)              // Explicitly set thread count 
            .batchProcessor(csvToJsonConverter)
            .outputToCSV("data/transformation_stats.csv")
            .build()
            .execute();
            
        System.out.println("Data transformation complete! CSV converted to JSON.");
    }
    
    /**
     * Converts a CSV line to JSON format
     */
    private static String convertCsvLineToJson(String csvLine) {
        // Skip header line
        if (csvLine.startsWith("id,name,email")) {
            return null;
        }
        
        String[] parts = csvLine.split(",");
        if (parts.length < 3) {
            return null; // Skip invalid lines
        }
        
        // Simple CSV to JSON conversion
        return String.format("{\"id\":\"%s\",\"name\":\"%s\",\"email\":\"%s\"}", 
            parts[0].trim(), parts[1].trim(), parts[2].trim());
    }
}
