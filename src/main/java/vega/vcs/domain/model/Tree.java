package vega.vcs.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tree domain model
 * Represents a directory object
 */
public class Tree implements VegaObject {
    private final List<TreeEntry> entries;
    private final Hash hash;

    public Tree() {
        this.entries = new ArrayList<>();
        this.hash = calculateHash();
    }

    public void addEntry(TreeEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Entry cannot be null");
        }
        entries.add(entry);
    }

    public void addEntry(VegaObjectType type, Hash hash, String name) {
        addEntry(TreeEntry.of(type, hash, name));
    }

    public List<TreeEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    @Override
    public VegaObjectType getType() {
        return VegaObjectType.TREE;
    }

    @Override
    public Hash getHash() {
        return hash;
    }

    @Override
    public byte[] getStorageBytes() {
        // simple textual representation: each line as "type hash name\n"
        StringBuilder sb = new StringBuilder();
        for (TreeEntry e : entries) {
            sb.append(e.toString()).append("\n");
        }
        byte[] content = sb.toString().getBytes(StandardCharsets.UTF_8);
        String header = "tree " + content.length + "\0";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[headerBytes.length + content.length];
        System.arraycopy(headerBytes, 0, out, 0, headerBytes.length);
        System.arraycopy(content, 0, out, headerBytes.length, content.length);
        return out;
    }

    public static Tree fromContent(String content) {
        Tree t = new Tree();
        String[] lines = content.split("\n");
        for (String l : lines) {
            if (l.trim().isEmpty()) continue;
            String[] parts = l.split(" ", 3);
            if (parts.length == 3) {
                VegaObjectType type = VegaObjectType.fromString(parts[0]);
                Hash hash = Hash.of(parts[1]);
                t.addEntry(type, hash, parts[2]);
            }
        }
        return t;
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
