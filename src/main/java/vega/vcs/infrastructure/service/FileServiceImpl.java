package vega.vcs.infrastructure.service;

import vega.vcs.domain.model.*;
import vega.vcs.domain.model.CommitObj;
import vega.vcs.domain.model.Tree;
import vega.vcs.domain.model.TreeEntry;
import vega.vcs.domain.model.VegaObjectType;
import vega.vcs.domain.repository.Repository;
import vega.vcs.domain.service.FileService;

import java.nio.file.*;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

/**
 * File service implementation
 * Handles file operations and staging
 * Follows Single Responsibility Principle and Dependency Inversion Principle
 */
public class FileServiceImpl implements FileService {

    private final Repository repository;

    public FileServiceImpl(Repository repository) {
        this.repository = repository;
    }

    @Override
    public void addFile(String filePath) throws Exception {
        // Handle "add ." command - add all files in the working directory
        if (".".equals(filePath)) {
            addAllFiles();
            return;
        }

        Path file = repository.getWorkDir().resolve(filePath);

        // Check if file exists in working directory
        if (!file.toFile().exists()) {
            // Check if file exists in HEAD (for deletion staging)
            if (fileExistsInHEAD(filePath)) {
                stageFileDeletion(filePath);
                return;
            } else {
                System.out.println("File not found: " + filePath);
                return;
            }
        }

        if (!Files.isRegularFile(file)) {
            // Handle empty directories
            if (Files.isDirectory(file)) {
                stageEmptyDirectory(filePath);
                return;
            }
            System.out.println("Cannot add directory: " + filePath + ". Use 'add .' to add all files.");
            return;
        }

        addSingleFile(filePath, file);
    }

    private void addAllFiles() throws Exception {
        System.out.println("Adding modified and new files from working directory...");
        Path workDir = repository.getWorkDir();
        Path dhaDir = repository.getDhaDir();

        // Get current index and HEAD files for comparison
        Map<String, String> index = repository.readIndex();
        Set<String> headFiles = getFilesFromHEAD();

        int addedCount = 0;
        int stagedDeletions = 0;

        // First, handle files that exist in working directory
        try (Stream<Path> stream = Files.walk(workDir)) {
            for (Path file : stream.toList()) {
                // Skip .vega directory and its contents
                if (file.startsWith(dhaDir)) {
                    continue;
                }

                // Skip target directory and its contents (Maven build output)
                Path targetDir = workDir.resolve("target");
                if (file.startsWith(targetDir)) {
                    continue;
                }

                // Only process regular files
                if (Files.isRegularFile(file)) {
                    String relativePath = workDir.relativize(file).toString().replace("\\", "/");

                    // Check if file needs to be added (new or modified)
                    if (shouldAddFile(relativePath, file, index, headFiles)) {
                        addSingleFile(relativePath, file);
                        addedCount++;
                    }
                }
            }
        }

        // Second, handle files that were deleted from working directory but exist in HEAD
        for (String headFile : headFiles) {
            Path filePath = workDir.resolve(headFile);
            if (!Files.exists(filePath)) {
                // File was deleted from working directory
                if (!index.containsKey(headFile) || !index.get(headFile).isEmpty()) {
                    // File is not staged for deletion yet
                    stageFileDeletion(headFile);
                    stagedDeletions++;
                }
            }
        }

        System.out.println("Added " + addedCount + " files and staged " + stagedDeletions + " deletions to staging area.");
    }

    private void addSingleFile(String filePath, Path file) throws Exception {
        System.out.println("Adding single file: " + filePath);
        byte[] storage = repository.blobObjectFromFile(file);
        String hash = repository.writeObject(storage);
        Map<String, String> index = repository.readIndex();
        index.put(filePath, hash);
        repository.writeIndex(index);
        System.out.println("Added " + filePath + " as blob " + hash);
    }

    private boolean fileExistsInHEAD(String filePath) throws Exception {
        Optional<String> headCommit = repository.readHEAD();
        if (headCommit.isEmpty() || headCommit.get().isEmpty()) {
            return false;
        }

        // Check if file exists in HEAD commit
        Optional<byte[]> raw = repository.readObjectRaw(headCommit.get());
        if (raw.isEmpty()) return false;

        String content = new String(raw.get(), java.nio.charset.StandardCharsets.UTF_8);
        int nul = content.indexOf('\0');
        if (nul >= 0) content = content.substring(nul + 1);

        CommitObj c = CommitObj.fromContent(content);
        return getFileContentFromTree(filePath, c.getTreeHash().getValue()) != null;
    }

    private void stageFileDeletion(String filePath) throws Exception {
        Map<String, String> index = repository.readIndex();
        // Mark file for deletion by setting hash to empty string
        index.put(filePath, "");
        repository.writeIndex(index);
        System.out.println("Staged deletion of " + filePath);
    }

    private void stageEmptyDirectory(String filePath) throws Exception {
        // For empty directories, we don't need to do anything special
        // Just acknowledge that it was processed
        System.out.println("Processed empty directory: " + filePath);
    }

    private String getFileContentFromTree(String filePath, String treeHash) throws Exception {
        String[] pathParts = filePath.split("/");
        String currentHash = treeHash;

        for (int i = 0; i < pathParts.length - 1; i++) {
            Optional<byte[]> raw = repository.readObjectRaw(currentHash);
            if (raw.isEmpty()) return null;

            String content = new String(raw.get(), java.nio.charset.StandardCharsets.UTF_8);
            int nul = content.indexOf('\0');
            if (nul >= 0) content = content.substring(nul + 1);
            Tree t = Tree.fromContent(content);

            boolean found = false;
            for (TreeEntry e : t.getEntries()) {
                if (e.getName().equals(pathParts[i]) && e.getType() == VegaObjectType.TREE) {
                    currentHash = e.getHash().getValue();
                    found = true;
                    break;
                }
            }
            if (!found) return null;
        }

        Optional<byte[]> raw = repository.readObjectRaw(currentHash);
        if (raw.isEmpty()) return null;

        String content = new String(raw.get(), java.nio.charset.StandardCharsets.UTF_8);
        int nul = content.indexOf('\0');
        if (nul >= 0) content = content.substring(nul + 1);
        Tree t = Tree.fromContent(content);

        for (TreeEntry e : t.getEntries()) {
            if (e.getName().equals(pathParts[pathParts.length - 1]) && e.getType() == VegaObjectType.BLOB) {
                return e.getHash().getValue();
            }
        }

        return null;
    }

    /**
     * Determines if a file should be added to the staging area
     * Returns true if the file is new, modified, or needs to be re-staged
     */
    private boolean shouldAddFile(String filePath, Path file, Map<String, String> index, Set<String> headFiles) throws Exception {
        // 1. Önce ignore kontrolü yap
        if (isIgnored(file)) {
            return false;
        }

        // Get current file hash
        byte[] storage = repository.blobObjectFromFile(file);
        byte[] sha = java.security.MessageDigest.getInstance("SHA-1").digest(storage);
        String currentHash = repository.bytesToHex(sha);

        // Check if file is already staged with the same content
        if (index.containsKey(filePath)) {
            String stagedHash = index.get(filePath);
            if (stagedHash.equals(currentHash)) {
                // File is already staged with current content, no need to add again
                return false;
            }
        }

        // Check if file exists in HEAD
        if (headFiles.contains(filePath)) {
            // File exists in HEAD, check if it's modified
            String headHash = getFileHashFromHEAD(filePath);
            if (!headHash.equals(currentHash)) {
                // File is modified from HEAD
                return true;
            }
        } else {
            // File is completely new (not in HEAD)
            return true;
        }

        return false;
    }

    /**
     * Gets all files from HEAD commit
     */
    private Set<String> getFilesFromHEAD() throws Exception {
        Set<String> headFiles = new HashSet<>();
        Optional<String> headCommit = repository.readHEAD();
        if (headCommit.isPresent() && !headCommit.get().isEmpty()) {
            Optional<byte[]> raw = repository.readObjectRaw(headCommit.get());
            if (raw.isPresent()) {
                String content = new String(raw.get(), java.nio.charset.StandardCharsets.UTF_8);
                int nul = content.indexOf('\0');
                if (nul >= 0) content = content.substring(nul + 1);
                CommitObj c = CommitObj.fromContent(content);
                headFiles = getFilesInTree("", c.getTreeHash().getValue());
            }
        }
        return headFiles;
    }

    /**
     * Gets all files in a tree recursively
     */
    private Set<String> getFilesInTree(String prefix, String treeHash) throws Exception {
        Set<String> files = new HashSet<>();
        Optional<byte[]> raw = repository.readObjectRaw(treeHash);
        if (raw.isEmpty()) return files;

        String content = new String(raw.get(), java.nio.charset.StandardCharsets.UTF_8);
        int nul = content.indexOf('\0');
        if (nul >= 0) content = content.substring(nul + 1);
        Tree t = Tree.fromContent(content);

        for (TreeEntry e : t.getEntries()) {
            String path = prefix.isEmpty() ? e.getName() : prefix + "/" + e.getName();
            if (e.getType() == VegaObjectType.BLOB) {
                files.add(path);
            } else if (e.getType() == VegaObjectType.TREE) {
                files.addAll(getFilesInTree(path, e.getHash().getValue()));
            }
        }
        return files;
    }

    /**
     * Checks if a file should be ignored based on .dignore patterns
     */
    private boolean isIgnored(Path filePath) {
        try {
            Path workDir = repository.getWorkDir();
            String relativePath = workDir.relativize(filePath).toString().replace("\\", "/");

            // Default ignore patterns (similar to .gitignore)
            if (relativePath.startsWith(".idea/") ||
                    relativePath.startsWith("target/") ||
                    relativePath.startsWith(".mvn/") ||
                    relativePath.startsWith(".vega/") ||
                    relativePath.equals(".idea") ||
                    relativePath.equals("target") ||
                    relativePath.equals(".mvn") ||
                    relativePath.equals(".vega")) {
                return true;
            }

            // Check .dignore file if it exists
            Path dignoreFile = workDir.resolve(".dignore");
            if (!Files.exists(dignoreFile)) {
                return false;
            }

            // Read .dignore file
            List<String> ignorePatterns = Files.readAllLines(dignoreFile);

            for (String pattern : ignorePatterns) {
                pattern = pattern.trim();

                // Skip comments and empty lines
                if (pattern.isEmpty() || pattern.startsWith("#")) {
                    continue;
                }

                // Handle directory patterns (ending with /)
                if (pattern.endsWith("/")) {
                    String dirPattern = pattern.substring(0, pattern.length() - 1);
                    if (relativePath.startsWith(dirPattern + "/") || relativePath.equals(dirPattern)) {
                        return true;
                    }
                }
                // Handle exact file matches
                else if (relativePath.equals(pattern)) {
                    return true;
                }
                // Handle wildcard patterns
                else if (pattern.contains("*")) {
                    if (matchesWildcard(relativePath, pattern)) {
                        return true;
                    }
                }
                // Handle directory patterns without trailing slash
                else if (relativePath.startsWith(pattern + "/")) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            // If we can't read .dignore, don't ignore anything
            return false;
        }
    }

    /**
     * Simple wildcard matching for * patterns
     */
    private boolean matchesWildcard(String path, String pattern) {
        String regex = pattern.replace("*", ".*");
        return path.matches(regex);
    }

    /**
     * Gets file hash from HEAD commit
     */
    private String getFileHashFromHEAD(String filePath) throws Exception {
        Optional<String> headCommit = repository.readHEAD();
        if (headCommit.isPresent() && !headCommit.get().isEmpty()) {
            Optional<byte[]> raw = repository.readObjectRaw(headCommit.get());
            if (raw.isPresent()) {
                String content = new String(raw.get(), java.nio.charset.StandardCharsets.UTF_8);
                int nul = content.indexOf('\0');
                if (nul >= 0) content = content.substring(nul + 1);
                CommitObj c = CommitObj.fromContent(content);
                return getFileHashFromTree(filePath, c.getTreeHash().getValue());
            }
        }
        return "";
    }

    /**
     * Gets file hash from tree structure
     */
    private String getFileHashFromTree(String filePath, String treeHash) throws Exception {
        String[] pathParts = filePath.split("/");
        String currentHash = treeHash;

        for (int i = 0; i < pathParts.length - 1; i++) {
            Optional<byte[]> raw = repository.readObjectRaw(currentHash);
            if (raw.isEmpty()) return "";

            String content = new String(raw.get(), java.nio.charset.StandardCharsets.UTF_8);
            int nul = content.indexOf('\0');
            if (nul >= 0) content = content.substring(nul + 1);
            Tree t = Tree.fromContent(content);

            boolean found = false;
            for (TreeEntry e : t.getEntries()) {
                if (e.getName().equals(pathParts[i]) && e.getType() == VegaObjectType.TREE) {
                    currentHash = e.getHash().getValue();
                    found = true;
                    break;
                }
            }
            if (!found) return "";
        }

        Optional<byte[]> raw = repository.readObjectRaw(currentHash);
        if (raw.isEmpty()) return "";

        String content = new String(raw.get(), java.nio.charset.StandardCharsets.UTF_8);
        int nul = content.indexOf('\0');
        if (nul >= 0) content = content.substring(nul + 1);
        Tree t = Tree.fromContent(content);

        for (TreeEntry e : t.getEntries()) {
            if (e.getName().equals(pathParts[pathParts.length - 1]) && e.getType() == VegaObjectType.BLOB) {
                return e.getHash().getValue();
            }
        }

        return "";
    }
}
