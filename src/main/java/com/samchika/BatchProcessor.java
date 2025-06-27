package com.samchika;

import java.util.List;

@FunctionalInterface
public interface BatchProcessor {

    List<String> processBatch(List<String> batch);

}
