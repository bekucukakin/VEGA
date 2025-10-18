package vega.vcs.domain.service;

/**
 * File service interface
 * Handles file operations and staging
 * Follows Single Responsibility Principle
 */
public interface FileService {

    /**
     * Adds a file to the staging area
     * @param filePath the path to the file to add
     * @throws Exception if file cannot be added
     */
    void addFile(String filePath) throws Exception;
}
