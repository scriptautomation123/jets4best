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
        logger.error("event=error type=minimal message=Caused by: {}", cause.getMessage());
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
}