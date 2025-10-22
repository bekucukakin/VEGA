package vega.vcs.domain.service;

/**
 * Status service interface
 * Handles file status and diff operations
 * Follows Single Responsibility Principle
 */
public interface StatusService {

    /**
     * Shows the current status of the working directory
     * @throws Exception if status cannot be determined
     */
    void showStatus() throws Exception;

    /**
     * Shows differences for a specific file
     * @param filePath the file to show differences for
     * @throws Exception if diff cannot be shown
     */
    void showDiff(String filePath) throws Exception;
}
