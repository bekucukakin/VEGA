package vega.vcs.domain.model;

/**
 * Git object type enumeration
 * Represents the different types of Git objects
 */
public enum VegaObjectType {
    BLOB("blob"),
    TREE("tree"),
    COMMIT("commit"),
    TAG("tag");

    private final String value;

    VegaObjectType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static VegaObjectType fromString(String value) {
        for (VegaObjectType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown Git object type: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
