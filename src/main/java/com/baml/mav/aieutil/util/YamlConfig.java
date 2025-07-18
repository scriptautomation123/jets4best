package com.baml.mav.aieutil.util;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class YamlConfig {
    private final Map<String, Object> config;

    public YamlConfig(String path) {
        this.config = loadConfig(path);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadConfig(String path) {
        try {
            ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

            // Only load from file system, never from classpath
            File configFile = new File(path);
            Logger logger = LoggingUtils.getLogger(YamlConfig.class);
            logger.info("Loading config from file system: {}", configFile.getAbsolutePath());

            return yaml.readValue(configFile, Map.class);
        } catch (Exception e) {
            throw ExceptionUtils.wrap(
                    e,
                    "Could not find configuration file: " + path + "\n" +
                            "• Ensure " + path + " exists in the file system.\n" +
                            "• Specify the correct path with -Dvault.config=/path/to/vaults.yaml\n" +
                            "Original error: " + e.getMessage());
        }
    }

    public String getRawValue(String key) {
        String[] parts = key.split("\\.");
        Object current = config;
        for (String part : parts) {
            if (current instanceof Map<?, ?>) {
                Map<?, ?> map = (Map<?, ?>) current;
                current = map.get(part);
            } else {
                return null;
            }
        }
        return current != null ? current.toString() : null;
    }

    public Map<String, Object> getAll() {
        return config;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getVaultEntryById(String id) {
        Object vaultsObj = config.get("vaults");
        if (vaultsObj instanceof List<?>) {
            List<?> vaultsList = (List<?>) vaultsObj;
            for (Object entry : vaultsList) {
                if (entry instanceof Map<?, ?>) {
                    Map<?, ?> map = (Map<?, ?>) entry;
                    Object entryId = map.get("id");
                    if (entryId != null && entryId.toString().equals(id)) {
                        return (Map<String, Object>) map;
                    }
                }
            }
        }
        return Collections.emptyMap();
    }
}