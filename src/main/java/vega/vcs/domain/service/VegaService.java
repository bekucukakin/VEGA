package vega.vcs.domain.service;

/**
 * Vega service interface
 * Defines the contract for Vega operations
 * Follows Interface Segregation Principle
 */
public interface VegaService {

    // Repository management
    void init() throws Exception;

    // File operations
    void add(String filePath) throws Exception;
    void commit(String message) throws Exception;

    // Status and history
    void status() throws Exception;
    void log() throws Exception;
    void diff(String filePath) throws Exception;
    void diff(String filePath, String mode) throws Exception;

    // Branch and checkout operations
    void checkout(String target) throws Exception;
    void checkoutFile(String filePath) throws Exception;
    void branch(String name) throws Exception;
    void listBranches() throws Exception;

    // Merge operations
    void merge(String branchName) throws Exception;
}
