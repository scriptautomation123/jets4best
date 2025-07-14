package com.baml.mav.aieutil.util;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

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
            InputStream in = getClass().getClassLoader().getResourceAsStream(path);
            if (in != null) {
                return yaml.readValue(in, Map.class);
            } else {
                return yaml.readValue(new File(path), Map.class);
            }
        } catch (Exception e) {
            throw ExceptionUtils.wrap(
                    e,
                    "Could not find configuration file: " + path + "\n" +
                            "• If running from your IDE, ensure " + path + " is in src/main/resources.\n" +
                            "• If running as a fat jar, ensure " + path
                            + " is in the same directory as the jar or specify -Dconfig.file/-Dvault.config.\n" +
                            "Original error: " + e.getMessage());
        }
    }

    public String getRawValue(String key) {
        String[] parts = key.split("\\.");
        Object current = config;
        for (String part : parts) {
            if (current instanceof Map<?, ?> map) {
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
        if (vaultsObj instanceof java.util.List<?> vaultsList) {
            for (Object entry : vaultsList) {
                if (entry instanceof Map<?, ?> map) {
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