package com.baml.mav.aieutil.service;

import java.io.PrintStream;
import java.util.Map;

public class SimpleOutputFormatter implements OutputFormatter {
    @Override
    public void formatResult(Map<String, Object> result, PrintStream out) {
        if (result == null || result.isEmpty()) {
            return;
        }
        
        if (result.size() == 1) {
            Object value = result.values().iterator().next();
            out.println(value != null ? value.toString() : "null");
        } else {
            result.forEach((key, value) -> out.println(key + ": " + value));
        }
    }
}