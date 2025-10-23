package vega.vcs.domain.service;

/**
 * Checkout service interface
 * Handles checkout and branch operations
 * Follows Single Responsibility Principle
 */
public interface CheckoutService {

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
}
