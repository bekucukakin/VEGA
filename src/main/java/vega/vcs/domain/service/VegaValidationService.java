package vega.vcs.domain.service;

/**
 * Git validation service interface
 * Handles pre-operation validations to ensure Git-compliant behavior
 * Follows Single Responsibility Principle
 */
public interface VegaValidationService {

    /**
     * Validates if a checkout operation can be performed
     * @param target the target commit or branch
     * @throws Exception if checkout cannot be performed
     */
    void validateCheckout(String target) throws Exception;

    /**
     * Validates if a merge operation can be performed
     * @param branchName the branch to merge
     * @throws Exception if merge cannot be performed
     */
    void validateMerge(String branchName) throws Exception;

    /**
     * Validates the working directory state
     * @throws Exception if working directory is in invalid state
     */
    void validateWorkingDirectory() throws Exception;

    /**
     * Validates file operations
     * @throws Exception if file operations cannot be performed
     */
    void validateFileOperations() throws Exception;

    /**
     * Validates if a branch can be created
     * @param branchName the branch name
     * @throws Exception if branch cannot be created
     */
    void validateBranchCreation(String branchName) throws Exception;

    /**
     * Validates if a commit can be created
     * @throws Exception if commit cannot be created
     */
    void validateCommit() throws Exception;

    /**
     * Validates if a file can be added to staging
     * @param filePath the file path
     * @throws Exception if file cannot be added
     */
    void validateFileAdd(String filePath) throws Exception;

    /**
     * Validates if a file can be removed from staging
     * @param filePath the file path
     * @throws Exception if file cannot be removed
     */
    void validateFileRemove(String filePath) throws Exception;
}


