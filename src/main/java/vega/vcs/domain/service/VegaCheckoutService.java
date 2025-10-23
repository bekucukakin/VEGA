package vega.vcs.domain.service;

/**
 * Git-compliant checkout service interface
 * Handles checkout operations with full Git compliance
 * Follows Single Responsibility Principle
 */
public interface VegaCheckoutService {

    /**
     * Checks out to the specified target (commit or branch)
     * @param target the commit hash or branch name
     * @throws Exception if checkout fails
     */
    void checkout(String target) throws Exception;

    /**
     * Restores a specific file from HEAD commit
     * @param filePath the file to restore
     * @throws Exception if file cannot be restored
     */
    void checkoutFile(String filePath) throws Exception;

    /**
     * Creates a new branch with the given name
     * @param name the branch name
     * @throws Exception if branch cannot be created
     */
    void createBranch(String name) throws Exception;

    /**
     * Checks if a checkout operation can be performed
     * @param target the target commit or branch
     * @return true if checkout can be performed
     * @throws Exception if check fails
     */
    boolean canCheckout(String target) throws Exception;

    /**
     * Validates the working directory before checkout
     * @throws Exception if working directory is not in valid state for checkout
     */
    void validateWorkingDirectory() throws Exception;

    /**
     * Checks if the target exists (commit or branch)
     * @param target the target to check
     * @return true if target exists
     * @throws Exception if check fails
     */
    boolean targetExists(String target) throws Exception;

    /**
     * Gets the current branch name
     * @return current branch name or null if in detached HEAD state
     * @throws Exception if current branch cannot be determined
     */
    String getCurrentBranch() throws Exception;

    /**
     * Checks if currently in detached HEAD state
     * @return true if in detached HEAD state
     * @throws Exception if state cannot be determined
     */
    boolean isDetachedHead() throws Exception;

    /**
     * Lists all available branches
     * @throws Exception if branches cannot be listed
     */
    void listBranches() throws Exception;
}


