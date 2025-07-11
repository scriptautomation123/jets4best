package com.baml.mav.aieutil.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.InputStream;
import java.util.Map;

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
            throw new RuntimeException("Failed to load YAML config: " + path, e);
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
}