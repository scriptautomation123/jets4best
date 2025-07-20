package com.baml.mav.aieutil.service;

import java.io.PrintStream;
import java.util.Map;

public final class ExecutionResult {
    private final int exitCode;
    private final Map<String, Object> data;
    private final String message;

    private ExecutionResult(int exitCode, Map<String, Object> data, String message) {
        this.exitCode = exitCode;
        this.data = data;
        this.message = message;
    }

    public int getExitCode() {
        return exitCode;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public String getMessage() {
        return message;
    }

    public void formatOutput(PrintStream out) {
        if (message != null) {
            out.println(message);
        }
        if (data != null && !data.isEmpty()) {
            if (data.size() == 1) {
                Object value = data.values().iterator().next();
                out.println(value != null ? value.toString() : "null");
            } else {
                data.forEach((key, value) -> out.println(key + ": " + value));
            }
        }
    }

    public static ExecutionResult success(Map<String, Object> data) {
        return new ExecutionResult(0, data, null);
    }

    public static ExecutionResult success(String message) {
        return new ExecutionResult(0, null, message);
    }

    public static ExecutionResult failure(int exitCode, String message) {
        return new ExecutionResult(exitCode, null, message);
    }

    public static ExecutionResult passwordOnlyMode() {
        return new ExecutionResult(0, null, "=== VAULT PASSWORD DECRYPTION ===\nSuccess: true");
    }
}