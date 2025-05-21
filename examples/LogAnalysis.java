import com.samchika.SmartFileProcessor;
import com.samchika.LineProcessor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Example demonstrating how to use Samchika for log file analysis.
 * 
 * This example:
 * 1. Filters log entries to extract only error messages
 * 2. Adds timestamps to each extracted error
 * 3. Demonstrates performance monitoring with stats output
 */
public class LogAnalysis {

    // Pattern for identifying log lines with errors
    private static final Pattern ERROR_PATTERN = Pattern.compile(".*ERROR.*");
    
    // Format for timestamp annotation
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        // Define our line processor 
        LineProcessor errorExtractor = new LineProcessor() {
            @Override
            public String processLine(String line) {
                Matcher matcher = ERROR_PATTERN.matcher(line);
                if (matcher.matches()) {
                    // Add a timestamp to error messages
                    String timestamp = LocalDateTime.now().format(FORMATTER);
                    return "[EXTRACTED: " + timestamp + "] " + line;
                }
                // Return null to exclude this line from output
                return null;
            }
        };
        
        // Configure and run the processor
        SmartFileProcessor.builder()
            .inputPath("logs/application.log")
            .outputPath("analysis/errors_extracted.log")
            .lineProcessor(errorExtractor) 
            .displayStats(true)           // Show performance statistics
            .outputToJson("analysis/processing_metrics.json")
            .build()
            .execute();
            
        System.out.println("Log analysis complete! Errors extracted to analysis/errors_extracted.log");
    }
}
