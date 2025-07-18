package com.baml.mav.aieutil.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

public final class LoggingUtils {
    private LoggingUtils() {
    }

    public static void configureLogger(String loggerName, Level level) {
        Configurator.setLevel(loggerName, level);
    }

    public static Logger getLogger(Class<?> clazz) {
        return LogManager.getLogger(clazz);
    }

    public static Logger getLogger(String name) {
        return LogManager.getLogger(name);
    }

    public static void logConnection(String dbType, String host, int port, String database) {
        Logger logger = getLogger(LoggingUtils.class);
        logger.info("event=connect dbType={} host={} port={} database={}", dbType, host, port, database);
    }

    public static void logConnectionUrl(String url) {
        Logger logger = getLogger(LoggingUtils.class);
        logger.info("event=connect_url url={}", url);
    }

    public static void logMinimalError(Throwable exception) {
        Logger logger = getLogger(LoggingUtils.class);
        Throwable cause = getRootCause(exception);
        StackTraceElement[] stack = cause.getStackTrace();
        String location = "";
        if (stack != null && stack.length > 0) {
            location = String.format(" (at %s:%d)", stack[0].getClassName(), stack[0].getLineNumber());
        }
        String msg = String.format("[ERROR] Caused by: %s: %s%s",
                cause.getClass().getSimpleName(),
                cause.getMessage(),
                location);
        logger.error(msg);
        System.err.println(msg); // Direct to console, regardless of logger config
    }

    public static void logStructuredError(String event, String operation, String errorType, String message,
            Throwable t) {
        Logger logger = getLogger(LoggingUtils.class);
        logger.error("event={} operation={} errorType={} message={}", event, operation, errorType, message, t);
    }

    private static Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    public static void configureMinimalConsoleLogging() {
        Configurator.setRootLevel(Level.ERROR);
        Configurator.setLevel("com.baml.mav.jdbcsqlrunner", Level.DEBUG);
    }

    public static String getCallerInfo() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        if (stack.length > 3) {
            StackTraceElement caller = stack[3]; // Skip getCallerInfo, current method, and immediate caller
            return caller.getClassName() + "." + caller.getMethodName();
        }
        return "unknown";
    }
}