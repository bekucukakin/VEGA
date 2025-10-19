package vega.vcs.domain.service;

/**
 * Commit service interface
 * Handles commit operations and history
 * Follows Single Responsibility Principle
 */
public interface CommitService {

    /**
     * Creates a new commit with the given message
     * @param message the commit message
     * @throws Exception if commit cannot be created
     */
    void createCommit(String message) throws Exception;

    /**
     * Shows the commit history
     * @throws Exception if history cannot be shown
     */
    void showLog() throws Exception;
}
