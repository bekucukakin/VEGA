package vega.vcs.domain.model;

import java.util.Objects;

/**
 * Tree entry domain model
 * Represents an entry in a Git tree object
 */
public final class TreeEntry {
    private final VegaObjectType type;
    private final Hash hash;
    private final String name;

    public TreeEntry(VegaObjectType type, Hash hash, String name) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }
        if (hash == null) {
            throw new IllegalArgumentException("Hash cannot be null");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        
        this.type = type;
        this.hash = hash;
        this.name = name.trim();
    }

    public VegaObjectType getType() {
        return type;
    }

    public Hash getHash() {
        return hash;
    }

    public String getName() {
        return name;
    }

    public boolean isBlob() {
        return type == VegaObjectType.BLOB;
    }

    public boolean isTree() {
        return type == VegaObjectType.TREE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TreeEntry treeEntry = (TreeEntry) o;
        return type == treeEntry.type &&
                Objects.equals(hash, treeEntry.hash) &&
                Objects.equals(name, treeEntry.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, hash, name);
    }

    @Override
    public String toString() {
        return type.getValue() + " " + hash.getValue() + " " + name;
    }

    public static TreeEntry of(VegaObjectType type, Hash hash, String name) {
        return new TreeEntry(type, hash, name);
    }

    public static TreeEntry blob(Hash hash, String name) {
        return new TreeEntry(VegaObjectType.BLOB, hash, name);
    }

    public static TreeEntry tree(Hash hash, String name) {
        return new TreeEntry(VegaObjectType.TREE, hash, name);
    }
}
