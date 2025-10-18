package vega.vcs.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Blob domain model
 * Represents a file object
 */
public class Blob implements VegaObject {
    private final byte[] content;
    private final Hash hash;

    public Blob(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null");
        }
        this.content = content;
        this.hash = calculateHash();
    }

    public byte[] getContent() {
        return content;
    }

    @Override
    public VegaObjectType getType() {
        return VegaObjectType.BLOB;
    }

    @Override
    public Hash getHash() {
        return hash;
    }

    @Override
    public byte[] getStorageBytes() {
        // Vega-style header: "blob {len}\0" + content
        String header = "blob " + content.length + "\0";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[headerBytes.length + content.length];
        System.arraycopy(headerBytes, 0, out, 0, headerBytes.length);
        System.arraycopy(content, 0, out, headerBytes.length, content.length);
        return out;
    }

    private Hash calculateHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] storageBytes = getStorageBytes();
            byte[] hashBytes = digest.digest(storageBytes);
            return Hash.of(bytesToHex(hashBytes));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not available", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
