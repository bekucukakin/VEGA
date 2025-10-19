package vega.vcs.domain.service;

import vega.vcs.domain.model.FileState;
import vega.vcs.domain.model.WorkingDirectoryState;

/**
 * File state service interface
 * Handles file state management and working directory state
 * Follows Single Responsibility Principle
 */
public interface FileStateService {

    /**
     * Gets the complete working directory state
     * @return WorkingDirectoryState object containing all file states
     * @throws Exception if state cannot be determined
     */
    WorkingDirectoryState getWorkingDirectoryState() throws Exception;

    /**
     * Gets the state of a specific file
     * @param filePath the file path
     * @return FileState of the file
     * @throws Exception if state cannot be determined
     */
    FileState getFileState(String filePath) throws Exception;

    /**
     * Updates the state of a specific file
     * @param filePath the file path
     * @param state the new state
     * @throws Exception if state cannot be updated
     */
    void updateFileState(String filePath, FileState state) throws Exception;

    /**
     * Checks if there are uncommitted changes in the working directory
     * @return true if there are uncommitted changes
     * @throws Exception if check fails
     */
    boolean hasUncommittedChanges() throws Exception;

    /**
     * Checks if there are staged changes
     * @return true if there are staged changes
     * @throws Exception if check fails
     */
    boolean hasStagedChanges() throws Exception;

    /**
     * Checks if there are unstaged changes
     * @return true if there are unstaged changes
     * @throws Exception if check fails
     */
    boolean hasUnstagedChanges() throws Exception;

    /**
     * Checks if the working directory is clean (no uncommitted changes)
     * @return true if working directory is clean
     * @throws Exception if check fails
     */
    boolean isWorkingDirectoryClean() throws Exception;

    /**
     * Gets all files with a specific state
     * @param state the file state to filter by
     * @return set of file paths with the specified state
     * @throws Exception if files cannot be retrieved
     */
    java.util.Set<String> getFilesWithState(FileState state) throws Exception;
}


