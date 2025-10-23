package vega.vcs.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Merge state model
 * Represents the state of an ongoing merge operation
 * Follows Git's merge state model
 */
public class MergeState {
    private final String mergeHead;
    private final String mergeMsg;
    private final List<String> conflictedFiles;
    private final boolean isInProgress;
    private final String targetBranch;

    public MergeState(String mergeHead, String mergeMsg, List<String> conflictedFiles,
                      boolean isInProgress, String targetBranch) {
        this.mergeHead = mergeHead;
        this.mergeMsg = mergeMsg;
        this.conflictedFiles = new ArrayList<>(conflictedFiles);
        this.isInProgress = isInProgress;
        this.targetBranch = targetBranch;
    }

    public String getMergeHead() {
        return mergeHead;
    }

    public String getMergeMsg() {
        return mergeMsg;
    }

    public List<String> getConflictedFiles() {
        return Collections.unmodifiableList(conflictedFiles);
    }

    public boolean isInProgress() {
        return isInProgress;
    }

    public String getTargetBranch() {
        return targetBranch;
    }

    public boolean hasConflicts() {
        return !conflictedFiles.isEmpty();
    }

    public boolean canComplete() {
        return isInProgress && !hasConflicts();
    }

    public static MergeState createInProgress(String mergeHead, String targetBranch) {
        String mergeMsg = "Merge branch '" + targetBranch + "'";
        return new MergeState(mergeHead, mergeMsg, new ArrayList<>(), true, targetBranch);
    }

    public static MergeState createWithConflicts(String mergeHead, String targetBranch, 
                                               List<String> conflictedFiles) {
        String mergeMsg = "Merge branch '" + targetBranch + "'";
        return new MergeState(mergeHead, mergeMsg, conflictedFiles, true, targetBranch);
    }

    public static MergeState createCompleted() {
        return new MergeState("", "", new ArrayList<>(), false, "");
    }
}


