package vega.vcs.domain.model;

/**
 * File state enumeration
 * Represents the state of a file in the working directory
 * Follows Git's file state model
 */
public enum FileState {
    /**
     * File is unmodified (same as HEAD)
     */
    UNMODIFIED,
    
    /**
     * File has been modified but not staged
     */
    MODIFIED,
    
    /**
     * File has been staged for commit
     */
    STAGED,
    
    /**
     * File is not tracked by Git
     */
    UNTRACKED,
    
    /**
     * File has been deleted
     */
    DELETED,
    
    /**
     * File has been renamed
     */
    RENAMED,
    
    /**
     * File has merge conflicts
     */
    CONFLICTED
}


