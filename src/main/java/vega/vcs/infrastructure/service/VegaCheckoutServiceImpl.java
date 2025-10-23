package vega.vcs.infrastructure.service;

import vega.vcs.domain.model.CommitObj;
import vega.vcs.domain.model.Tree;
import vega.vcs.domain.model.TreeEntry;
import vega.vcs.domain.model.VegaObjectType;
import vega.vcs.domain.repository.Repository;
import vega.vcs.domain.service.FileStateService;
import vega.vcs.domain.service.VegaCheckoutService;
import vega.vcs.domain.service.VegaValidationService;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Git-compliant checkout service implementation
 * Handles checkout operations with full Git compliance
 * Follows Single Responsibility Principle and Dependency Inversion Principle
 */
public class VegaCheckoutServiceImpl implements VegaCheckoutService {

    private final Repository repository;
    private final VegaValidationService validationService;

    public VegaCheckoutServiceImpl(Repository repository, FileStateService fileStateService,
                                   VegaValidationService validationService) {
        this.repository = repository;
        this.validationService = validationService;
    }

    @Override
    public void checkout(String target) throws Exception {
        // Handle special cases first
        if (target.equals("HEAD")) {
            // HEAD checkout - restore working directory to HEAD
            // This should fail if there are uncommitted changes
            validationService.validateCheckout(target);
            
            // Get current HEAD commit and restore working directory
            Optional<String> headCommit = repository.readHEAD();
            if (headCommit.isPresent() && !headCommit.get().isEmpty()) {
                restoreTreeToWorkdir(headCommit.get());
                System.out.println("Restored working directory to HEAD");
            } else {
                System.out.println("HEAD checkout: no commits yet");
            }
            return;
        }
        
        // Validate checkout operation
        validationService.validateCheckout(target);
        
        // Check if target is a branch
        Path branchRef = repository.getDhaDir().resolve("refs/heads").resolve(target);
        if (Files.exists(branchRef)) {
            checkoutBranch(target);
        } else {
            checkoutCommit(target);
        }
    }

    @Override
    public void checkoutFile(String filePath) throws Exception {
        // Validate file operations
        validationService.validateFileOperations();
        
        // Get HEAD commit
        Optional<String> headCommit = repository.readHEAD();
        if (headCommit.isEmpty() || headCommit.get().isEmpty()) {
            throw new Exception("fatal: You are on a branch yet to be born");
        }
        
        // Get file content from HEAD
        String fileContent = getFileContentFromHEAD(filePath, headCommit.get());
        if (fileContent == null) {
            throw new Exception("error: pathspec '" + filePath + "' did not match any file(s) known to vega");
        }
        
        // Write file to working directory
        Path file = repository.getWorkDir().resolve(filePath);
        Files.createDirectories(file.getParent() != null ? file.getParent() : repository.getWorkDir());
        Files.write(file, fileContent.getBytes());
        
        System.out.println("Updated " + filePath);
    }

    @Override
    public void createBranch(String name) throws Exception {
        // Validate branch creation
        validationService.validateBranchCreation(name);
        
        Optional<String> head = repository.readHEAD();
        String commit = head.orElse("");
        if (commit.isBlank()) {
            // Create empty branch
            repository.updateRef("refs/heads/" + name, "");
            System.out.println("Created branch " + name + " (no commits)");
            return;
        }
        repository.updateRef("refs/heads/" + name, commit);
        System.out.println("Created branch " + name + " at " + commit);
    }

    @Override
    public boolean canCheckout(String target) throws Exception {
        try {
            validationService.validateCheckout(target);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void validateWorkingDirectory() throws Exception {
        validationService.validateWorkingDirectory();
    }

    @Override
    public boolean targetExists(String target) throws Exception {
        // Check if it's a branch
        Optional<String> branchCommit = repository.readRef("refs/heads/" + target);
        if (branchCommit.isPresent() && !branchCommit.get().isEmpty()) {
            return true;
        }
        
        // Check if it's a commit hash
        Optional<byte[]> commitRaw = repository.readObjectRaw(target);
        if (commitRaw.isPresent()) {
            return true;
        }
        
        // Check for short hash
        if (target.length() >= 6 && target.length() < 40) {
            return findObjectByPrefix(target).isPresent();
        }
        
        return false;
    }

    @Override
    public String getCurrentBranch() throws Exception {
        String headRaw = repository.readHEADRaw();
        if (headRaw.startsWith("ref: ")) {
            String ref = headRaw.substring(5).trim();
            if (ref.startsWith("refs/heads/")) {
                return ref.substring(11); // Remove "refs/heads/" prefix
            }
        }
        return null;
    }

    @Override
    public boolean isDetachedHead() throws Exception {
        String headRaw = repository.readHEADRaw();
        return !headRaw.startsWith("ref: ");
    }

    private void checkoutBranch(String branchName) throws Exception {
        // Set HEAD to branch reference
        repository.setHEADToRef("refs/heads/" + branchName);
        
        // Get branch commit
        Optional<String> commit = repository.readRef("refs/heads/" + branchName);
        if (commit.isPresent() && !commit.get().isEmpty()) {
            restoreTreeToWorkdir(commit.get());
            System.out.println("Switched to branch '" + branchName + "'");
        } else {
            System.out.println("Switched to branch '" + branchName + "' (no commits yet)");
        }
    }

    private void checkoutCommit(String target) throws Exception {
        String commitHash = target;
        
        // Handle short hash
        if (commitHash.length() >= 6 && commitHash.length() < 40) {
            Optional<String> found = findObjectByPrefix(commitHash);
            if (found.isPresent()) {
                commitHash = found.get();
            } else {
                throw new Exception("fatal: reference is not a tree: " + target);
            }
        }
        
        // Check if commit exists
        Optional<byte[]> raw = repository.readObjectRaw(commitHash);
        if (raw.isEmpty()) {
            throw new Exception("fatal: reference is not a tree: " + target);
        }
        
        // Set HEAD to detached state
        repository.setHEADDetached(commitHash);
        restoreTreeToWorkdir(commitHash);
        System.out.println("HEAD is now at " + commitHash);
    }

    private void restoreTreeToWorkdir(String commitHash) throws Exception {
        // Read commit, get tree hash
        Optional<byte[]> raw = repository.readObjectRaw(commitHash);
        if (raw.isEmpty()) return;
        
        String content = new String(raw.get(), java.nio.charset.StandardCharsets.UTF_8);
        int nul = content.indexOf('\0');
        if (nul >= 0) content = content.substring(nul + 1);
        CommitObj c = CommitObj.fromContent(content);
        String treeHash = c.getTreeHash().getValue();

        // Get files that should exist in target commit
        Set<String> targetFiles = getFilesInTree("", treeHash);

        // Get current working directory files
        Set<String> currentFiles = Files.walk(repository.getWorkDir())
                .filter(Files::isRegularFile)
                .filter(p -> !p.startsWith(repository.getDhaDir()))
                .filter(p -> !p.startsWith(repository.getWorkDir().resolve(".git")))
                .map(p -> repository.getWorkDir().relativize(p).toString().replace("\\", "/"))
                .collect(Collectors.toSet());

        // Only remove files that don't exist in target commit AND are not important system files
        for (String filePath : currentFiles) {
            if (!targetFiles.contains(filePath)) {
                // Don't delete important files like pom.xml, src/, target/, etc.
                if (!isImportantFile(filePath)) {
                    Path file = repository.getWorkDir().resolve(filePath);
                    Files.deleteIfExists(file);
                }
            }
        }

        // Restore/update files from target commit
        restoreTree("", treeHash);
    }
    
    private boolean isImportantFile(String filePath) {
        // Don't delete important project files
        return filePath.equals("pom.xml") ||
               filePath.startsWith("src/") ||
               filePath.startsWith("target/") ||
               filePath.startsWith(".idea/") ||
               filePath.startsWith(".mvn/") ||
               filePath.startsWith(".vega/") ||
               isIgnoredByDignore(filePath);
    }
    
    private boolean isIgnoredByDignore(String filePath) {
        try {
            Path workDir = repository.getWorkDir();
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
                    if (filePath.startsWith(dirPattern + "/") || filePath.equals(dirPattern)) {
                        return true;
                    }
                }
                // Handle exact file matches
                else if (filePath.equals(pattern)) {
                    return true;
                }
                // Handle wildcard patterns
                else if (pattern.contains("*")) {
                    if (matchesWildcard(filePath, pattern)) {
                        return true;
                    }
                }
                // Handle directory patterns without trailing slash
                else if (filePath.startsWith(pattern + "/")) {
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

    private String getFileContentFromHEAD(String filePath, String commitHash) throws Exception {
        // Read commit, get tree hash
        Optional<byte[]> raw = repository.readObjectRaw(commitHash);
        if (raw.isEmpty()) return null;
        
        String content = new String(raw.get(), java.nio.charset.StandardCharsets.UTF_8);
        int nul = content.indexOf('\0');
        if (nul >= 0) content = content.substring(nul + 1);
        CommitObj c = CommitObj.fromContent(content);
        String treeHash = c.getTreeHash().getValue();
        
        // Navigate through tree to find the file
        String[] pathParts = filePath.split("/");
        String currentHash = treeHash;

        for (int i = 0; i < pathParts.length - 1; i++) {
            // Navigate to subdirectory
            Optional<byte[]> treeRaw = repository.readObjectRaw(currentHash);
            if (treeRaw.isEmpty()) return null;

            String treeContent = new String(treeRaw.get(), java.nio.charset.StandardCharsets.UTF_8);
            int treeNul = treeContent.indexOf('\0');
            if (treeNul >= 0) treeContent = treeContent.substring(treeNul + 1);
            Tree t = Tree.fromContent(treeContent);

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

        // Get the file content
        Optional<byte[]> fileRaw = repository.readObjectRaw(currentHash);
        if (fileRaw.isEmpty()) return null;

        String fileTreeContent = new String(fileRaw.get(), java.nio.charset.StandardCharsets.UTF_8);
        int fileTreeNul = fileTreeContent.indexOf('\0');
        if (fileTreeNul >= 0) fileTreeContent = fileTreeContent.substring(fileTreeNul + 1);
        Tree fileTree = Tree.fromContent(fileTreeContent);

        for (TreeEntry e : fileTree.getEntries()) {
            if (e.getName().equals(pathParts[pathParts.length - 1]) && e.getType() == VegaObjectType.BLOB) {
                Optional<byte[]> blobRaw = repository.readObjectRaw(e.getHash().getValue());
                if (blobRaw.isPresent()) {
                    byte[] blobData = blobRaw.get();
                    int blobNul = -1;
                    for (int i = 0; i < blobData.length; i++) {
                        if (blobData[i] == 0) {
                            blobNul = i;
                            break;
                        }
                    }
                    if (blobNul >= 0) {
                        return new String(Arrays.copyOfRange(blobData, blobNul + 1, blobData.length));
                    } else {
                        return new String(blobData);
                    }
                }
            }
        }

        return null;
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

    private void restoreTree(String prefix, String treeHash) throws Exception {
        Optional<byte[]> raw = repository.readObjectRaw(treeHash);
        if (raw.isEmpty()) return;
        
        String content = new String(raw.get(), java.nio.charset.StandardCharsets.UTF_8);
        int nul = content.indexOf('\0');
        if (nul >= 0) content = content.substring(nul + 1);
        Tree t = Tree.fromContent(content);
        
        for (TreeEntry e : t.getEntries()) {
            String path = prefix.isEmpty() ? e.getName() : prefix + "/" + e.getName();
            if (e.getType() == VegaObjectType.BLOB) {
                Optional<byte[]> bro = repository.readObjectRaw(e.getHash().getValue());
                if (bro.isPresent()) {
                    byte[] data = bro.get();
                    // Remove header
                    int n = -1;
                    for (int i = 0; i < data.length; i++) {
                        if (data[i] == 0) {
                            n = i;
                            break;
                        }
                    }
                    if (n >= 0) data = Arrays.copyOfRange(data, n + 1, data.length);
                    
                    Path out = repository.getWorkDir().resolve(path);
                    Files.createDirectories(out.getParent() == null ? repository.getWorkDir() : out.getParent());
                    Files.write(out, data);
                }
            } else if (e.getType() == VegaObjectType.TREE) {
                restoreTree(path, e.getHash().getValue());
            }
        }
    }

    private Optional<String> findObjectByPrefix(String prefix) throws Exception {
        Path objs = repository.getDhaDir().resolve("objects");
        if (!Files.exists(objs)) return Optional.empty();
        
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(objs)) {
            for (Path p : ds) {
                if (!Files.isDirectory(p)) continue;
                String dirName = p.getFileName().toString();
                try (DirectoryStream<Path> files = Files.newDirectoryStream(p)) {
                    for (Path f : files) {
                        String name = f.getFileName().toString();
                        String full = dirName + name;
                        if (full.startsWith(prefix)) return Optional.of(full);
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void listBranches() throws Exception {
        Path refsDir = repository.getDhaDir().resolve("refs/heads");
        List<String> branches = new ArrayList<>();
        
        // Get current branch first
        String currentBranch = getCurrentBranch();
        
        // Add current branch if it exists but not in refs/heads (like master)
        if (currentBranch != null && !currentBranch.isEmpty()) {
            branches.add(currentBranch);
        }
        
        // Add branches from refs/heads directory
        if (Files.exists(refsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(refsDir)) {
                for (Path branchFile : stream) {
                    if (Files.isRegularFile(branchFile)) {
                        String branchName = branchFile.getFileName().toString();
                        if (!branches.contains(branchName)) {
                            branches.add(branchName);
                        }
                    }
                }
            }
        }

        if (branches.isEmpty()) {
            System.out.println("No branches found");
            return;
        }

        // Sort branches
        branches.sort(String::compareTo);

        System.out.println("Available branches:");
        for (String branch : branches) {
            if (branch.equals(currentBranch)) {
                System.out.println("* " + branch + " (current)");
            } else {
                System.out.println("  " + branch);
            }
        }
    }
}
