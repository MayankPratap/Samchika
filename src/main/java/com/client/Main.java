package com.client;

import com.samchika.SmartFileProcessor;

public class Main {

    private static String processLine(String line){

        StringBuilder builder = new StringBuilder();

        // simulate CPU-bound work
        for(int i=0; i<100; i++){

            builder.append("Hello ");
        }

        String str = builder.toString();

        int hash=0;

        for(int i=0;i<10000; ++i){

            // do nothing just compute hash again and again.
            hash = str.hashCode();
        }

        return Integer.toString(hash);
    }


    // This is the client side code which calls my SmartFileProcessor

    public static void main(String[] args){

        SmartFileProcessor processor = SmartFileProcessor.builder()
                .inputPath("5GBFile.txt")
                .outputPath("output.txt")
                .lineProcessor(Main::processLine)
                .displayStats(true)
                .build();

        processor.execute();

    }


}
