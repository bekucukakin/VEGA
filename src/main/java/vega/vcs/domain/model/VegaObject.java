package vega.vcs.domain.model;

/**
 * Git object interface
 * Defines common behavior for all Git objects (blob, tree, commit)
 */
public interface VegaObject {
    /**
     * Get the type of this Git object
     * @return the Git object type
     */
    VegaObjectType getType();

    /**
     * Get the serialized bytes for storage
     * @return byte array representing this object for storage
     */
    byte[] getStorageBytes();

    /**
     * Get the hash of this object
     * @return the hash of this object
     */
    Hash getHash();
}
