package vega.vcs.domain.model;

/**
 * Conflict information model
 * Represents merge conflict information for a file
 * Follows Git's conflict model
 */
public class ConflictInfo {
    private final String filePath;
    private final String baseContent;
    private final String oursContent;
    private final String theirsContent;
    private final ConflictType type;

    public ConflictInfo(String filePath, String baseContent, String oursContent,
                        String theirsContent, ConflictType type) {
        this.filePath = filePath;
        this.baseContent = baseContent;
        this.oursContent = oursContent;
        this.theirsContent = theirsContent;
        this.type = type;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getBaseContent() {
        return baseContent;
    }

    public String getOursContent() {
        return oursContent;
    }

    public String getTheirsContent() {
        return theirsContent;
    }

    public ConflictType getType() {
        return type;
    }

    public boolean isResolvable() {
        return type != ConflictType.UNRESOLVABLE;
    }

    public String getConflictMarkers() {
        StringBuilder sb = new StringBuilder();
        sb.append("<<<<<<< HEAD\n");
        sb.append(oursContent);
        sb.append("\n=======\n");
        sb.append(theirsContent);
        sb.append("\n>>>>>>> ").append(filePath).append("\n");
        return sb.toString();
    }

    public enum ConflictType {
        /**
         * Both sides modified the same lines
         */
        BOTH_MODIFIED,
        
        /**
         * One side deleted, other modified
         */
        DELETED_MODIFIED,
        
        /**
         * Both sides deleted
         */
        BOTH_DELETED,
        
        /**
         * One side added, other modified
         */
        ADDED_MODIFIED,
        
        /**
         * Cannot be automatically resolved
         */
        UNRESOLVABLE
    }
}


