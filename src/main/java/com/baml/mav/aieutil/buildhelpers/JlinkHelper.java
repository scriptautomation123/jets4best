package com.baml.mav.aieutil.buildhelpers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JlinkHelper {
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 3) {
            System.err.println("Usage: java ...JlinkHelper <path-to-jar> <path-to-jmods> <output-jre-dir>"); // NOSONAR
            System.exit(1);
        }
        Path jarPath = Paths.get(args[0]);
        Path jmodsPath = Paths.get(args[1]);
        Path outputJreDir = Paths.get(args[2]);

        if (!Files.exists(jarPath)) {
            System.err.println("JAR file does not exist: " + jarPath); // NOSONAR
            System.exit(2);
        }
        if (!Files.exists(jmodsPath) || !Files.isDirectory(jmodsPath)) {
            System.err.println("jmods directory does not exist: " + jmodsPath); // NOSONAR
            System.exit(3);
        }

        // 1. Run jdeps to get required modules
        String[] jdepsCmd = new String[] {
                "jdeps",
                "--print-module-deps",
                "--ignore-missing-deps",
                "--multi-release", "21",
                "--recursive",
                jarPath.toString()
        };
        System.out.println("Running jdeps..."); // NOSONAR
        String modules = runAndCapture(jdepsCmd);
        if (modules == null || modules.trim().isEmpty()) {
            System.err.println("jdeps did not return any modules. Aborting."); // NOSONAR
            System.exit(4);
        }
        modules = modules.trim();
        System.out.println("jdeps modules: " + modules); // NOSONAR

        // 2. Run jlink to build the custom JRE
        String[] jlinkCmd = new String[] {
                "jlink",
                "--module-path", jmodsPath.toString(),
                "--add-modules", modules,
                "--output", outputJreDir.toString(),
                "--strip-debug",
                "--no-man-pages",
                "--no-header-files",
                "--compress", "zip-2"
        };
        System.out.println("Running jlink..."); // NOSONAR
        int jlinkExit = runAndStream(jlinkCmd);
        if (jlinkExit != 0) {
            System.err.println("jlink failed with exit code: " + jlinkExit); // NOSONAR
            System.exit(5);
        }
        System.out.println("Custom JRE created at: " + outputJreDir); // NOSONAR
    }

    private static String runAndCapture(String[] cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        int exit = proc.waitFor();
        if (exit != 0) {
            System.err.println("Command failed: " + String.join(" ", cmd)); // NOSONAR
            System.exit(10);
        }
        return sb.toString();
    }

    private static int runAndStream(String[] cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line); // NOSONAR
            }
        }
        return proc.waitFor();
    }
}