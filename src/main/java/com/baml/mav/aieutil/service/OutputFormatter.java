package com.baml.mav.aieutil.service;

import java.io.PrintStream;

public interface OutputFormatter {
    void formatResult(java.util.Map<String, Object> result, PrintStream out);
}
