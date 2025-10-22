package vega.vcs.infrastructure.service;

import vega.vcs.domain.model.CommitObj;
import vega.vcs.domain.model.Tree;
import vega.vcs.domain.model.TreeEntry;
import vega.vcs.domain.model.VegaObjectType;
import vega.vcs.domain.repository.Repository;
import vega.vcs.domain.service.StatusService;
import vega.vcs.shared.util.ColorOutput;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Status service implementation
 * Handles file status and diff operations
 * Follows Single Responsibility Principle and Dependency Inversion Principle
 */
public class StatusServiceImpl implements StatusService {

    private final Repository repository;

    public StatusServiceImpl(Repository repository) {
        this.repository = repository;
    }

    @Override
    public void showStatus() throws Exception {
        Map<String, String> index = repository.readIndex();

        // Get files from HEAD commit for comparison
        Set<String> headFiles = getFilesFromHEAD();

        // Get working directory files
        Path workDir = repository.getWorkDir();
        Path targetDir = workDir.resolve("target");
        List<Path> allFiles = Files.walk(workDir)
                .filter(Files::isRegularFile)
                .filter(p -> !p.startsWith(repository.getDhaDir()))
                .filter(p -> !p.startsWith(targetDir))
                .filter(p -> !isIgnored(p))
                .collect(Collectors.toList());

        Set<String> workingFiles = allFiles.stream()
                .map(p -> repository.getWorkDir().relativize(p).toString().replace("\\", "/"))
                .collect(Collectors.toSet());

        // Categorize files
        List<String> stagedFiles = new ArrayList<>();
        List<String> modifiedFiles = new ArrayList<>();
        List<String> newFiles = new ArrayList<>();
        List<String> deletedFiles = new ArrayList<>();

        categorizeFiles(index, headFiles, workingFiles, stagedFiles, modifiedFiles, newFiles, deletedFiles);

        displayStatus(stagedFiles, modifiedFiles, newFiles, deletedFiles);
    }

    @Override
    public void showDiff(String filePath) throws Exception {
        Path file = repository.getWorkDir().resolve(filePath);
        if (!Files.exists(file)) {
            System.out.println("File not found: " + filePath);
            return;
        }

        showFileDiff(filePath);
    }

    /**
     * Shows a side-by-side diff view
     */
    public void showSideBySideDiff(String filePath) throws Exception {
        Path file = repository.getWorkDir().resolve(filePath);
        if (!Files.exists(file)) {
            System.out.println("File not found: " + filePath);
            return;
        }

        // Get current file content
        List<String> currentLines = Files.readAllLines(file);
        // Get HEAD file content
        List<String> headLines = getFileContentFromHEAD(filePath);

        System.out.println(ColorOutput.colorize("=== Side-by-Side Diff: " + filePath + " ===", ColorOutput.BOLD + ColorOutput.CYAN));
        System.out.println(ColorOutput.colorize("OLD (HEAD)", ColorOutput.BOLD + ColorOutput.RED) + " | " + 
                         ColorOutput.colorize("NEW (Working)", ColorOutput.BOLD + ColorOutput.GREEN));
        System.out.println(ColorOutput.colorize("=" + "=".repeat(50) + "=", ColorOutput.CYAN));

        int maxLines = Math.max(currentLines.size(), headLines.size());
        for (int i = 0; i < maxLines; i++) {
            String oldLine = i < headLines.size() ? headLines.get(i) : "";
            String newLine = i < currentLines.size() ? currentLines.get(i) : "";
            
            if (oldLine.equals(newLine)) {
                // Lines are the same
                System.out.println(String.format("%-25s | %s", 
                    ColorOutput.colorize(oldLine.length() > 25 ? oldLine.substring(0, 22) + "..." : oldLine, ColorOutput.YELLOW),
                    ColorOutput.colorize(newLine, ColorOutput.YELLOW)));
            } else {
                // Lines are different
                System.out.println(String.format("%-25s | %s", 
                    ColorOutput.colorize(oldLine.length() > 25 ? oldLine.substring(0, 22) + "..." : oldLine, ColorOutput.RED),
                    ColorOutput.colorize(newLine, ColorOutput.GREEN)));
            }
        }
        System.out.println();
    }

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

    private void categorizeFiles(Map<String, String> index, Set<String> headFiles, Set<String> workingFiles,
                                 List<String> stagedFiles, List<String> modifiedFiles,
                                 List<String> newFiles, List<String> deletedFiles) throws Exception {
        
        // Track which files we've already categorized
        Set<String> processedFiles = new HashSet<>();
        
        // Check staged files (files in index)
        for (Map.Entry<String, String> entry : index.entrySet()) {
            String filePath = entry.getKey();
            String stagedHash = entry.getValue();
            processedFiles.add(filePath);

            if (workingFiles.contains(filePath)) {
                // File exists in working directory - check if it's modified since staging
                Path file = repository.getWorkDir().resolve(filePath);
                byte[] storage = repository.blobObjectFromFile(file);
                byte[] sha = MessageDigest.getInstance("SHA-1").digest(storage);
                String currentHash = repository.bytesToHex(sha);

                if (!stagedHash.equals(currentHash)) {
                    // File is staged but also modified in working directory
                    modifiedFiles.add(filePath);
                }
                
                // Add to staged files (either new or modified)
                stagedFiles.add(filePath);
            } else {
                // File doesn't exist in working directory - staged for deletion
                deletedFiles.add(filePath);
                stagedFiles.add(filePath); // Add to staged files for deletion
            }
        }

        // Check unstaged changes (files not in index)
        for (String filePath : workingFiles) {
            if (!processedFiles.contains(filePath)) {
                if (headFiles.contains(filePath)) {
                    // File exists in HEAD - check if it's actually modified
                    Path file = repository.getWorkDir().resolve(filePath);
                    byte[] storage = repository.blobObjectFromFile(file);
                    byte[] sha = MessageDigest.getInstance("SHA-1").digest(storage);
                    String currentHash = repository.bytesToHex(sha);
                    
                    // Get HEAD file hash for comparison
                    String headHash = getFileHashFromHEAD(filePath);
                    
                    if (!headHash.equals(currentHash)) {
                        // File is actually modified from HEAD
                        modifiedFiles.add(filePath);
                    }
                    // If hashes are equal, file is clean (not modified)
                } else {
                    // Completely new file
                    newFiles.add(filePath);
                }
                processedFiles.add(filePath);
            }
        }
        
        // Check for files deleted from working directory (but not staged)
        for (String filePath : headFiles) {
            if (!processedFiles.contains(filePath) && !workingFiles.contains(filePath)) {
                // Only show as deleted if the file is not ignored by .dignore
                Path file = repository.getWorkDir().resolve(filePath);
                if (!isIgnored(file)) {
                    deletedFiles.add(filePath);
                }
            }
        }
    }

    private void displayStatus(List<String> stagedFiles, List<String> modifiedFiles,
                               List<String> newFiles, List<String> deletedFiles) throws Exception {
        // Get current branch name
        String currentBranch = getCurrentBranchName();
        System.out.println(ColorOutput.bold("On branch " + currentBranch));
        System.out.println();

        // Get HEAD files for comparison
        Set<String> headFiles = getFilesFromHEAD();
        
        // Separate staged files by type
        List<String> stagedNewFiles = new ArrayList<>();
        List<String> stagedModifiedFiles = new ArrayList<>();
        List<String> stagedDeletedFiles = new ArrayList<>();
        
        for (String file : stagedFiles) {
            if (deletedFiles.contains(file)) {
                stagedDeletedFiles.add(file);
            } else if (headFiles.contains(file)) {
                stagedModifiedFiles.add(file);
            } else {
                stagedNewFiles.add(file);
            }
        }
        
        // Separate unstaged files
        List<String> unstagedModifiedFiles = new ArrayList<>();
        List<String> unstagedNewFiles = new ArrayList<>();
        List<String> unstagedDeletedFiles = new ArrayList<>();
        
        for (String file : modifiedFiles) {
            if (!stagedFiles.contains(file)) {
                unstagedModifiedFiles.add(file);
            }
        }
        
        for (String file : newFiles) {
            if (!stagedFiles.contains(file)) {
                unstagedNewFiles.add(file);
            }
        }
        
        for (String file : deletedFiles) {
            if (!stagedFiles.contains(file)) {
                unstagedDeletedFiles.add(file);
            }
        }

        // Staged changes
        if (!stagedNewFiles.isEmpty() || !stagedModifiedFiles.isEmpty() || !stagedDeletedFiles.isEmpty()) {
            System.out.println(ColorOutput.colorize("Changes to be committed:", ColorOutput.BOLD + ColorOutput.GREEN));
            System.out.println(ColorOutput.colorize("  (use \"vega reset HEAD <file>...\" to unstage)", ColorOutput.BOLD + ColorOutput.GREEN));
            System.out.println();

            for (String file : stagedNewFiles) {
                System.out.println(ColorOutput.colorize("\tnew file:   ", ColorOutput.BOLD + ColorOutput.GREEN) + file);
            }
            for (String file : stagedModifiedFiles) {
                System.out.println(ColorOutput.colorize("\tmodified:   ", ColorOutput.BOLD + ColorOutput.GREEN) + file);
            }
            for (String file : stagedDeletedFiles) {
                System.out.println(ColorOutput.colorize("\tdeleted:    ", ColorOutput.BOLD + ColorOutput.GREEN) + file);
            }
            System.out.println();
        }

        // Unstaged changes
        if (!unstagedModifiedFiles.isEmpty() || !unstagedNewFiles.isEmpty() || !unstagedDeletedFiles.isEmpty()) {
            System.out.println(ColorOutput.colorize("Changes not staged for commit:", ColorOutput.BOLD + ColorOutput.RED));
            System.out.println(ColorOutput.colorize("  (use \"vega add <file>...\" to update what will be committed)", ColorOutput.BOLD + ColorOutput.RED));
            System.out.println(ColorOutput.colorize("  (use \"vega checkout -- <file>...\" to discard changes in working directory)", ColorOutput.BOLD + ColorOutput.RED));
            System.out.println();

            for (String file : unstagedModifiedFiles) {
                System.out.println(ColorOutput.colorize("\tmodified:   ", ColorOutput.BOLD + ColorOutput.RED) + file);
            }
            for (String file : unstagedDeletedFiles) {
                System.out.println(ColorOutput.colorize("\tdeleted:    ", ColorOutput.BOLD + ColorOutput.RED) + file);
            }
            System.out.println();
        }
        
        // Untracked files (Git format)
        if (!unstagedNewFiles.isEmpty()) {
            System.out.println(ColorOutput.colorize("Untracked files:", ColorOutput.BOLD + ColorOutput.RED));
            System.out.println(ColorOutput.colorize("  (use \"vega add <file>...\" to include in what will be committed)", ColorOutput.BOLD + ColorOutput.RED));
            System.out.println();
            
            for (String file : unstagedNewFiles) {
                System.out.println(ColorOutput.colorize("\t", ColorOutput.RED) + file);
            }
            System.out.println();
        }

        // Clean working directory
        if (stagedFiles.isEmpty() && modifiedFiles.isEmpty() && newFiles.isEmpty() && deletedFiles.isEmpty()) {
            System.out.println(ColorOutput.colorize("nothing to commit, working tree clean", ColorOutput.BOLD + ColorOutput.GREEN));
        }
    }

    private void showFileDiff(String filePath) throws Exception {
        Path file = repository.getWorkDir().resolve(filePath);
        if (!Files.exists(file)) return;

        // Get current file content
        List<String> currentLines = Files.readAllLines(file);

        // Get HEAD file content
        List<String> headLines = getFileContentFromHEAD(filePath);

        // Show diff
        showDiff(headLines, currentLines, filePath);
    }

    private List<String> getFileContentFromHEAD(String filePath) throws Exception {
        Optional<String> headCommit = repository.readHEAD();
        if (headCommit.isPresent() && !headCommit.get().isEmpty()) {
            Optional<byte[]> raw = repository.readObjectRaw(headCommit.get());
            if (raw.isPresent()) {
                String content = new String(raw.get(), java.nio.charset.StandardCharsets.UTF_8);
                int nul = content.indexOf('\0');
                if (nul >= 0) content = content.substring(nul + 1);
                CommitObj c = CommitObj.fromContent(content);
                return getFileContentFromTree(filePath, c.getTreeHash().getValue());
            }
        }
        return new ArrayList<>();
    }

    private List<String> getFileContentFromTree(String filePath, String treeHash) throws Exception {
        // Navigate through tree to find the file
        String[] pathParts = filePath.split("/");
        String currentHash = treeHash;

        for (int i = 0; i < pathParts.length - 1; i++) {
            // Navigate to subdirectory
            Optional<byte[]> raw = repository.readObjectRaw(currentHash);
            if (raw.isEmpty()) return new ArrayList<>();

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
            if (!found) return new ArrayList<>();
        }

        // Get the file content
        Optional<byte[]> raw = repository.readObjectRaw(currentHash);
        if (raw.isEmpty()) return new ArrayList<>();

        String content = new String(raw.get(), java.nio.charset.StandardCharsets.UTF_8);
        int nul = content.indexOf('\0');
        if (nul >= 0) content = content.substring(nul + 1);
        Tree t = Tree.fromContent(content);

        for (TreeEntry e : t.getEntries()) {
            if (e.getName().equals(pathParts[pathParts.length - 1]) && e.getType() == VegaObjectType.BLOB) {
                Optional<byte[]> blobRaw = repository.readObjectRaw(e.getHash().getValue());
                if (blobRaw.isPresent()) {
                    String blobContent = new String(blobRaw.get(), java.nio.charset.StandardCharsets.UTF_8);
                    int blobNul = blobContent.indexOf('\0');
                    if (blobNul >= 0) blobContent = blobContent.substring(blobNul + 1);
                    return Arrays.asList(blobContent.split("\n"));
                }
            }
        }

        return new ArrayList<>();
    }

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
    
    private String getFileHashFromTree(String filePath, String treeHash) throws Exception {
        // Navigate through tree to find the file
        String[] pathParts = filePath.split("/");
        String currentHash = treeHash;

        for (int i = 0; i < pathParts.length - 1; i++) {
            // Navigate to subdirectory
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

        // Get the file hash
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

    private String getCurrentBranchName() throws Exception {
        String headRaw = repository.readHEADRaw();
        if (headRaw.startsWith("ref: ")) {
            String ref = headRaw.substring(5).trim();
            if (ref.startsWith("refs/heads/")) {
                return ref.substring(11); // Remove "refs/heads/" prefix
            }
        }
        return "master"; // Default branch name
    }

    private void showDiff(List<String> oldLines, List<String> newLines, String filePath) {
        System.out.println(ColorOutput.colorize("diff --vega a/" + filePath + " b/" + filePath, ColorOutput.BOLD + ColorOutput.CYAN));
        System.out.println(ColorOutput.colorize("index 1234567..abcdefg 100644", ColorOutput.BOLD + ColorOutput.CYAN));
        System.out.println(ColorOutput.colorize("--- a/" + filePath, ColorOutput.BOLD + ColorOutput.CYAN));
        System.out.println(ColorOutput.colorize("+++ b/" + filePath, ColorOutput.BOLD + ColorOutput.CYAN));

        // Enhanced diff algorithm with better change detection
        List<DiffLine> diffLines = computeDiff(oldLines, newLines);
        displayDiffLines(diffLines);
        System.out.println();
    }

    /**
     * Represents a line in the diff with its type and content
     */
    private static class DiffLine {
        public enum Type { UNCHANGED, ADDED, REMOVED }
        public final Type type;
        public final String content;
        public final int oldLineNumber;
        public final int newLineNumber;

        public DiffLine(Type type, String content, int oldLineNumber, int newLineNumber) {
            this.type = type;
            this.content = content;
            this.oldLineNumber = oldLineNumber;
            this.newLineNumber = newLineNumber;
        }
    }

    /**
     * Computes a more sophisticated diff using a simple LCS-based algorithm
     */
    private List<DiffLine> computeDiff(List<String> oldLines, List<String> newLines) {
        List<DiffLine> result = new ArrayList<>();
        
        // Use dynamic programming to find Longest Common Subsequence
        int[][] lcs = new int[oldLines.size() + 1][newLines.size() + 1];
        
        // Build LCS table
        for (int i = 1; i <= oldLines.size(); i++) {
            for (int j = 1; j <= newLines.size(); j++) {
                if (oldLines.get(i - 1).equals(newLines.get(j - 1))) {
                    lcs[i][j] = lcs[i - 1][j - 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i - 1][j], lcs[i][j - 1]);
                }
            }
        }
        
        // Backtrack to find the actual diff
        int i = oldLines.size(), j = newLines.size();
        int oldLineNum = oldLines.size(), newLineNum = newLines.size();
        
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines.get(i - 1).equals(newLines.get(j - 1))) {
                // Lines are the same
                result.add(0, new DiffLine(DiffLine.Type.UNCHANGED, oldLines.get(i - 1), oldLineNum, newLineNum));
                i--;
                j--;
                oldLineNum--;
                newLineNum--;
            } else if (j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j])) {
                // Line added
                result.add(0, new DiffLine(DiffLine.Type.ADDED, newLines.get(j - 1), 0, newLineNum));
                j--;
                newLineNum--;
            } else {
                // Line removed
                result.add(0, new DiffLine(DiffLine.Type.REMOVED, oldLines.get(i - 1), oldLineNum, 0));
                i--;
                oldLineNum--;
            }
        }
        
        return result;
    }


    /**
     * Displays the diff lines with proper coloring and formatting
     */
    private void displayDiffLines(List<DiffLine> diffLines) {
        boolean inChangeBlock = false;
        int contextLines = 3; // Number of context lines to show
        
        for (int i = 0; i < diffLines.size(); i++) {
            DiffLine line = diffLines.get(i);
            
            // Determine if we're entering or leaving a change block
            boolean isChange = line.type == DiffLine.Type.ADDED || line.type == DiffLine.Type.REMOVED;
            boolean wasInChangeBlock = inChangeBlock;
            inChangeBlock = isChange;
            
            // Show context separator if entering a change block
            if (inChangeBlock && !wasInChangeBlock && i > 0) {
                System.out.println(ColorOutput.colorize("@@ -" + (i - contextLines) + ",+" + contextLines + " @@", ColorOutput.BOLD + ColorOutput.CYAN));
            }
            
            // Display the line with appropriate formatting
            switch (line.type) {
                case UNCHANGED:
                    if (inChangeBlock || wasInChangeBlock) {
                        // Show context lines
                        System.out.println(" " + String.format("%3d", line.oldLineNumber) + ": " + line.content);
                    }
                    break;
                    
                case ADDED:
                    System.out.println(ColorOutput.colorize("+" + String.format("%3d", line.newLineNumber) + ": ", ColorOutput.BOLD + ColorOutput.GREEN) + 
                                     ColorOutput.colorize(line.content, ColorOutput.GREEN));
                    break;
                    
                case REMOVED:
                    System.out.println(ColorOutput.colorize("-" + String.format("%3d", line.oldLineNumber) + ": ", ColorOutput.BOLD + ColorOutput.RED) + 
                                     ColorOutput.colorize(line.content, ColorOutput.RED));
                    break;
            }
        }
    }
    
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
    
    private boolean matchesWildcard(String path, String pattern) {
        // Simple wildcard matching for * patterns
        String regex = pattern.replace("*", ".*");
        return path.matches(regex);
    }
}

