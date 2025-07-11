package com.baml.mav.aieutil.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.Level;
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
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";
    private static final String YELLOW = "\u001B[33m";
    public static void logConnection(String dbType, String host, int port, String database) {
        System.out.printf("%s[CONNECT]%s %s%s%s:%s%s%s:%s%d%s database=%s%s%s%n",
            GREEN, RESET,           
            BLUE, dbType, RESET,    
            CYAN, host, RESET,      
            YELLOW, port, RESET,    
            BLUE, database, RESET   
        );
    }
    public static void logConnectionUrl(String url) {
        System.out.printf("%s[CONNECT]%s %s%s%s%n",
            GREEN, RESET,    
            CYAN, url, RESET 
        );
    }
    private static final String RED = "\u001B[31m";
    private static final String BOLD = "\u001B[1m";
    public static void logMinimalError(Throwable exception) {
        Throwable cause = getRootCause(exception);
        System.err.printf("%s%s[ERROR]%s %sCaused by:%s %s%n",
            RED, BOLD, RESET,      
            RED, RESET,            
            cause.getMessage()     
        );
    }
    private static Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
    public static void configureMinimalConsoleLogging() {
        Configurator.setRootLevel(org.apache.logging.log4j.Level.ERROR);
        Configurator.setLevel("com.baml.mav.jdbcsqlrunner", org.apache.logging.log4j.Level.DEBUG);
    }
} 