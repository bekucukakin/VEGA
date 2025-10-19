package vega.vcs.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Commit domain model
 * Represents a commit object
 */
public class CommitObj implements VegaObject {
    private final List<Hash> parents;
    private final Hash treeHash;
    private final String author;
    private final long timestamp; // epoch seconds
    private final String message;
    private final Hash hash;

    public CommitObj(Hash treeHash, List<Hash> parents, String author, long timestamp, String message) {
        if (treeHash == null) {
            throw new IllegalArgumentException("Tree hash cannot be null");
        }
        if (author == null || author.trim().isEmpty()) {
            throw new IllegalArgumentException("Author cannot be null or empty");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        this.treeHash = treeHash;
        this.parents = parents != null ? new ArrayList<>(parents) : new ArrayList<>();
        this.author = author.trim();
        this.timestamp = timestamp;
        this.message = message;
        this.hash = calculateHash();
    }

    public Hash getTreeHash() {
        return treeHash;
    }

    public List<Hash> getParents() {
        return Collections.unmodifiableList(parents);
    }

    public String getAuthor() {
        return author;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public VegaObjectType getType() {
        return VegaObjectType.COMMIT;
    }

    @Override
    public Hash getHash() {
        return hash;
    }

    @Override
    public byte[] getStorageBytes() {
        StringBuilder sb = new StringBuilder();
        sb.append("tree ").append(treeHash.getValue()).append("\n");
        for (Hash p : parents) sb.append("parent ").append(p.getValue()).append("\n");
        sb.append("author ").append(author).append(" ").append(timestamp).append("\n\n");
        sb.append(message).append("\n");
        byte[] content = sb.toString().getBytes(StandardCharsets.UTF_8);
        String header = "commit " + content.length + "\0";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[headerBytes.length + content.length];
        System.arraycopy(headerBytes, 0, out, 0, headerBytes.length);
        System.arraycopy(content, 0, out, headerBytes.length, content.length);
        return out;
    }

    public static CommitObj fromContent(String content) {
        // parse basic format above
        String[] parts = content.split("\n\n", 2);
        String metadata = parts[0];
        String msg = parts.length > 1 ? parts[1].trim() : "";
        String[] lines = metadata.split("\n");
        String tree = null;
        List<Hash> parents = new ArrayList<>();
        String authorLine = null;
        for (String l : lines) {
            if (l.startsWith("tree ")) tree = l.substring(5).trim();
            else if (l.startsWith("parent ")) parents.add(Hash.of(l.substring(7).trim()));
            else if (l.startsWith("author ")) authorLine = l.substring(7).trim();
        }
        String author = "";
        long ts = Instant.now().getEpochSecond();
        if (authorLine != null) {
            String[] aParts = authorLine.split(" ");
            if (aParts.length >= 2) {
                author = String.join(" ", Arrays.copyOfRange(aParts, 0, aParts.length - 1));
                try {
                    ts = Long.parseLong(aParts[aParts.length - 1]);
                } catch (NumberFormatException e) {
                    // ignore, ts default kalır
                }
            } else {
                author = authorLine;
            }
        }

        return new CommitObj(Hash.of(tree), parents, author, ts, msg);
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
