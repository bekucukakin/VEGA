package vega.vcs.domain.model;

import java.util.Objects;

/**
 * Hash value object
 * Represents a Git object hash with validation and type safety
 */
public final class Hash {
    private final String value;

    public Hash(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Hash cannot be null");
        }
        // Allow empty string for special cases (like empty tree)
        this.value = value.trim();
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Hash hash = (Hash) o;
        return Objects.equals(value, hash.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }

    public static Hash of(String value) {
        return new Hash(value);
    }
}
