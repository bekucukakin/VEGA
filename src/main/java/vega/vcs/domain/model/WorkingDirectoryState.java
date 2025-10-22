package vega.vcs.domain.model;

import java.util.*;

/**
 * Working directory state model
 * Represents the complete state of the working directory
 * Follows Git's working directory state model
 */
public class WorkingDirectoryState {
    private final Map<String, FileState> fileStates;
    private final Set<String> untrackedFiles;
    private final Set<String> stagedFiles;
    private final Set<String> modifiedFiles;
    private final Set<String> deletedFiles;
    private final Set<String> conflictedFiles;
    private final boolean hasUncommittedChanges;

    public WorkingDirectoryState(Map<String, FileState> fileStates,
                                Set<String> untrackedFiles,
                                Set<String> stagedFiles,
                                Set<String> modifiedFiles,
                                Set<String> deletedFiles,
                                Set<String> conflictedFiles,
                                boolean hasUncommittedChanges) {
        this.fileStates = new HashMap<>(fileStates);
        this.untrackedFiles = new HashSet<>(untrackedFiles);
        this.stagedFiles = new HashSet<>(stagedFiles);
        this.modifiedFiles = new HashSet<>(modifiedFiles);
        this.deletedFiles = new HashSet<>(deletedFiles);
        this.conflictedFiles = new HashSet<>(conflictedFiles);
        this.hasUncommittedChanges = hasUncommittedChanges;
    }

    public Map<String, FileState> getFileStates() {
        return Collections.unmodifiableMap(fileStates);
    }

    public Set<String> getUntrackedFiles() {
        return Collections.unmodifiableSet(untrackedFiles);
    }

    public Set<String> getStagedFiles() {
        return Collections.unmodifiableSet(stagedFiles);
    }

    public Set<String> getModifiedFiles() {
        return Collections.unmodifiableSet(modifiedFiles);
    }

    public Set<String> getDeletedFiles() {
        return Collections.unmodifiableSet(deletedFiles);
    }

    public Set<String> getConflictedFiles() {
        return Collections.unmodifiableSet(conflictedFiles);
    }

    public boolean hasUncommittedChanges() {
        return hasUncommittedChanges;
    }

    public FileState getFileState(String filePath) {
        return fileStates.getOrDefault(filePath, FileState.UNTRACKED);
    }

    public boolean isClean() {
        return !hasUncommittedChanges && conflictedFiles.isEmpty();
    }

    public boolean hasConflicts() {
        return !conflictedFiles.isEmpty();
    }

    public boolean hasStagedChanges() {
        return !stagedFiles.isEmpty() || !deletedFiles.isEmpty();
    }

    public boolean hasUnstagedChanges() {
        return !modifiedFiles.isEmpty() || !untrackedFiles.isEmpty() || !deletedFiles.isEmpty();
    }
}


