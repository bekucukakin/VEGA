package vega.vcs.domain.service;

import vega.vcs.domain.model.ConflictInfo;
import vega.vcs.domain.model.MergeState;

import java.util.List;

/**
 * Git-compliant merge service interface
 * Handles merge operations with full Git compliance
 * Follows Single Responsibility Principle
 */
public interface VegaMergeService {

    /**
     * Merges the specified branch into the current branch
     * @param branchName the branch to merge
     * @throws Exception if merge fails
     */
    void merge(String branchName) throws Exception;

    /**
     * Checks if a merge can be performed (no conflicts)
     * @param branchName the branch to check for merge conflicts
     * @return true if merge can be performed without conflicts
     * @throws Exception if check fails
     */
    boolean canMerge(String branchName) throws Exception;

    /**
     * Aborts an ongoing merge operation
     * @throws Exception if abort fails
     */
    void abortMerge() throws Exception;

    /**
     * Resolves a merge conflict for a specific file
     * @param filePath the file with conflict
     * @param resolution the conflict resolution
     * @throws Exception if conflict cannot be resolved
     */
    void resolveConflict(String filePath, ConflictResolution resolution) throws Exception;

    /**
     * Detects conflicts between current branch and target branch
     * @param branchName the target branch
     * @return list of conflict information
     * @throws Exception if conflict detection fails
     */
    List<ConflictInfo> detectConflicts(String branchName) throws Exception;

    /**
     * Gets the current merge state
     * @return current merge state or null if no merge in progress
     * @throws Exception if merge state cannot be determined
     */
    MergeState getMergeState() throws Exception;

    /**
     * Checks if a merge is in progress
     * @return true if merge is in progress
     * @throws Exception if state cannot be determined
     */
    boolean isMergeInProgress() throws Exception;

    /**
     * Completes a merge operation
     * @throws Exception if merge cannot be completed
     */
    void completeMerge() throws Exception;

    /**
     * Conflict resolution options
     */
    enum ConflictResolution {
        /**
         * Use our version (current branch)
         */
        OURS,
        
        /**
         * Use their version (target branch)
         */
        THEIRS,
        
        /**
         * Use both versions (manual resolution)
         */
        BOTH,
        
        /**
         * Delete the file
         */
        DELETE
    }
}


