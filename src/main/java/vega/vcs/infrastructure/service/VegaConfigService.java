package vega.vcs.infrastructure.service;

import vega.vcs.domain.repository.Repository;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Git config service implementation
 * Manages Git configuration files
 */
public class VegaConfigService {
    
    private final Repository repository;
    
    public VegaConfigService(Repository repository) {
        this.repository = repository;
    }
    
    /**
     * Gets a config value
     */
    public Optional<String> getConfig(String key) throws IOException {
        return getConfig(key, false);
    }
    
    /**
     * Gets a config value with global option
     */
    public Optional<String> getConfig(String key, boolean global) throws IOException {
        Path configFile = getConfigFile(global);
        if (!Files.exists(configFile)) return Optional.empty();
        
        List<String> lines = Files.readAllLines(configFile);
        for (String line : lines) {
            if (line.trim().startsWith(key + " = ")) {
                return Optional.of(line.substring(key.length() + 3).trim());
            }
        }
        return Optional.empty();
    }
    
    /**
     * Sets a config value
     */
    public void setConfig(String key, String value) throws IOException {
        setConfig(key, value, false);
    }
    
    /**
     * Sets a config value with global option
     */
    public void setConfig(String key, String value, boolean global) throws IOException {
        Path configFile = getConfigFile(global);
        Map<String, String> config = readConfigFile(configFile);
        config.put(key, value);
        writeConfigFile(configFile, config);
    }
    
    /**
     * Gets the config file path
     */
    private Path getConfigFile(boolean global) {
        if (global) {
            return Paths.get(System.getProperty("user.home")).resolve(".gitconfig");
        } else {
            return repository.getDhaDir().resolve("config");
        }
    }
    
    /**
     * Reads config file
     */
    private Map<String, String> readConfigFile(Path configFile) throws IOException {
        Map<String, String> config = new LinkedHashMap<>();
        if (!Files.exists(configFile)) return config;
        
        List<String> lines = Files.readAllLines(configFile);
        String currentSection = "";
        
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.substring(1, line.length() - 1);
            } else if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                String key = currentSection.isEmpty() ? parts[0].trim() : currentSection + "." + parts[0].trim();
                String value = parts[1].trim();
                config.put(key, value);
            }
        }
        return config;
    }
    
    /**
     * Writes config file
     */
    private void writeConfigFile(Path configFile, Map<String, String> config) throws IOException {
        Files.createDirectories(configFile.getParent());
        
        Map<String, List<String>> sections = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            if (key.contains(".")) {
                String[] parts = key.split("\\.", 2);
                String section = parts[0];
                String subKey = parts[1];
                
                sections.computeIfAbsent(section, k -> new ArrayList<>()).add(subKey + " = " + value);
            } else {
                sections.computeIfAbsent("", k -> new ArrayList<>()).add(key + " = " + value);
            }
        }
        
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : sections.entrySet()) {
            String section = entry.getKey();
            List<String> keys = entry.getValue();
            
            if (!section.isEmpty()) {
                lines.add("[" + section + "]");
            }
            lines.addAll(keys);
            lines.add("");
        }
        
        Files.write(configFile, lines);
    }
}
