package com.baml.mav.aieutil.service;

import java.io.PrintStream;

import org.apache.logging.log4j.Logger;

import com.baml.mav.aieutil.util.LoggingUtils;

public class ConsolePasswordPrompter implements PasswordPrompter {
    private final PrintStream out;
    private final Logger logger = LoggingUtils.getLogger(ConsolePasswordPrompter.class);

    public ConsolePasswordPrompter(PrintStream out) {
        this.out = out;
    }

    @Override
    public String promptForPassword() {
        out.print("Enter password: ");

        if (System.console() != null) {
            return readFromConsole();
        } else {
            return readFromScanner();
        }
    }

    private String readFromConsole() {
        try {
            return new String(System.console().readPassword());
        } catch (Exception e) {
            logger.warn("Failed to read from console: {}", e.getMessage());
            return null;
        }
    }

    private String readFromScanner() {
        try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
            return scanner.nextLine();
        } catch (Exception e) {
            logger.warn("Failed to read from scanner: {}", e.getMessage());
            return null;
        }
    }
}
