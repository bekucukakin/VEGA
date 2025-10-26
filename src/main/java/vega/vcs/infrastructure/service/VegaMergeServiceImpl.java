package vega.vcs.infrastructure.service;

import vega.vcs.domain.model.*;
import vega.vcs.domain.model.*;
import vega.vcs.domain.repository.Repository;
import vega.vcs.domain.service.FileStateService;
import vega.vcs.domain.service.VegaMergeService;
import vega.vcs.domain.service.VegaValidationService;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Git-compliant merge service implementation
 * Handles merge operations with full Git compliance
 * Follows Single Responsibility Principle and Dependency Inversion Principle
 */
public class VegaMergeServiceImpl implements VegaMergeService {

    private final Repository repository;
    private final VegaValidationService validationService;

    public VegaMergeServiceImpl(Repository repository, FileStateService fileStateService,
                              VegaValidationService validationService) {
        this.repository = repository;
        this.validationService = validationService;
    }

    @Override
    public void merge(String branchName) throws Exception {
        // Validate merge operation
        validationService.validateMerge(branchName);
        
        // Get current and target commits
        Optional<String> currentCommit = repository.readHEAD();
        Optional<String> targetCommit = repository.readRef("refs/heads/" + branchName);
        
        if (currentCommit.isEmpty() || currentCommit.get().isEmpty()) {
            throw new Exception("Cannot merge: no commits in current branch");
        }
        
        if (targetCommit.isEmpty() || targetCommit.get().isEmpty()) {
            throw new Exception("Branch '" + branchName + "' not found");
        }
        
        String currentCommitHash = currentCommit.get();
        String targetCommitHash = targetCommit.get();
        
        // Check if already up to date
        if (currentCommitHash.equals(targetCommitHash)) {
            System.out.println("Already up to date");
            return;
        }
        
        // Check for conflicts
        List<ConflictInfo> conflicts = detectConflicts(branchName);
        if (!conflicts.isEmpty()) {
            // Start merge with conflicts
            startMergeWithConflicts(currentCommitHash, targetCommitHash, branchName, conflicts);
            System.out.println("Automatic merge failed; fix conflicts and then commit the result.");
            return;
        }
        
        // Perform fast-forward or merge commit
        if (isFastForward(currentCommitHash, targetCommitHash)) {
            performFastForwardMerge(targetCommitHash, branchName);
        } else {
            performMergeCommit(currentCommitHash, targetCommitHash, branchName);
        }
    }

    @Override
    public boolean canMerge(String branchName) throws Exception {
        try {
            validationService.validateMerge(branchName);
            List<ConflictInfo> conflicts = detectConflicts(branchName);
            return conflicts.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void abortMerge() throws Exception {
        MergeState mergeState = getMergeState();
        if (mergeState == null || !mergeState.isInProgress()) {
            throw new Exception("fatal: There is no merge to abort (MERGE_HEAD missing)");
        }
        
        // Remove merge state files
        Files.deleteIfExists(repository.getDhaDir().resolve("MERGE_HEAD"));
        Files.deleteIfExists(repository.getDhaDir().resolve("MERGE_MSG"));
        
        System.out.println("Merge aborted");
    }

    @Override
    public void resolveConflict(String filePath, ConflictResolution resolution) throws Exception {
        MergeState mergeState = getMergeState();
        if (mergeState == null || !mergeState.isInProgress()) {
            throw new Exception("fatal: There is no merge to resolve");
        }
        
        if (!mergeState.getConflictedFiles().contains(filePath)) {
            throw new Exception("fatal: '" + filePath + "' is not in conflict");
        }
        
        // Apply resolution
        switch (resolution) {
            case OURS:
                resolveWithOurs(filePath);
                break;
            case THEIRS:
                resolveWithTheirs(filePath);
                break;
            case DELETE:
                Files.deleteIfExists(repository.getWorkDir().resolve(filePath));
                break;
            case BOTH:
                // Keep conflict markers for manual resolution
                break;
        }
        
        // Remove from conflicted files list
        List<String> remainingConflicts = mergeState.getConflictedFiles().stream()
                .filter(f -> !f.equals(filePath))
                .collect(Collectors.toList());
        
        if (remainingConflicts.isEmpty()) {
            // All conflicts resolved, complete merge
            completeMerge();
        }
    }

    @Override
    public List<ConflictInfo> detectConflicts(String branchName) throws Exception {
        Optional<String> currentCommit = repository.readHEAD();
        Optional<String> targetCommit = repository.readRef("refs/heads/" + branchName);
        
        if (currentCommit.isEmpty() || targetCommit.isEmpty()) {
            return new ArrayList<>();
        }
        
        String currentCommitHash = currentCommit.get();
        String targetCommitHash = targetCommit.get();
        
        // Find common ancestor
        String commonAncestor = findCommonAncestor(currentCommitHash, targetCommitHash);
        
        // Get file sets from all three commits
        Set<String> currentFiles = getFilesInCommit(currentCommitHash);
        Set<String> targetFiles = getFilesInCommit(targetCommitHash);
        Set<String> ancestorFiles = commonAncestor != null ? getFilesInCommit(commonAncestor) : new HashSet<>();
        
        List<ConflictInfo> conflicts = new ArrayList<>();
        
        // Check all files that exist in any of the commits
        Set<String> allFiles = new HashSet<>();
        allFiles.addAll(currentFiles);
        allFiles.addAll(targetFiles);
        allFiles.addAll(ancestorFiles);
        
        for (String filePath : allFiles) {
            ConflictInfo conflict = detectFileConflict(filePath, currentCommitHash, targetCommitHash, commonAncestor);
            if (conflict != null) {
                conflicts.add(conflict);
            }
        }
        
        return conflicts;
    }

    @Override
    public MergeState getMergeState() throws Exception {
        Path mergeHead = repository.getDhaDir().resolve("MERGE_HEAD");
        Path mergeMsg = repository.getDhaDir().resolve("MERGE_MSG");
        
        if (!Files.exists(mergeHead)) {
            return null;
        }
        
        String mergeHeadContent = Files.readString(mergeHead);
        String mergeMsgContent = Files.exists(mergeMsg) ? Files.readString(mergeMsg) : "";
        
        // Get conflicted files from working directory
        List<String> conflictedFiles = getConflictedFilesFromWorkingDirectory();
        
        return new MergeState(mergeHeadContent, mergeMsgContent, conflictedFiles, true, "");
    }

    @Override
    public boolean isMergeInProgress() throws Exception {
        MergeState mergeState = getMergeState();
        return mergeState != null && mergeState.isInProgress();
    }

    @Override
    public void completeMerge() throws Exception {
        MergeState mergeState = getMergeState();
        if (mergeState == null || !mergeState.isInProgress()) {
            throw new Exception("fatal: There is no merge to complete");
        }
        
        if (mergeState.hasConflicts()) {
            throw new Exception("fatal: You have not concluded your merge (conflicts still exist)");
        }
        
        // Create merge commit
        String mergeHead = mergeState.getMergeHead();
        Optional<String> currentCommit = repository.readHEAD();
        
        if (currentCommit.isPresent() && !currentCommit.get().isEmpty()) {
            createMergeCommit(currentCommit.get(), mergeHead, mergeState.getTargetBranch());
        }
        
        // Clean up merge state
        Files.deleteIfExists(repository.getDhaDir().resolve("MERGE_HEAD"));
        Files.deleteIfExists(repository.getDhaDir().resolve("MERGE_MSG"));
        
        System.out.println("Merge completed successfully");
    }

    private void startMergeWithConflicts(String currentCommit, String targetCommit, 
                                       String branchName, List<ConflictInfo> conflicts) throws Exception {
        // Write MERGE_HEAD
        Files.write(repository.getDhaDir().resolve("MERGE_HEAD"), targetCommit.getBytes());
        
        // Write MERGE_MSG
        String mergeMsg = "Merge branch '" + branchName + "'";
        Files.write(repository.getDhaDir().resolve("MERGE_MSG"), mergeMsg.getBytes());
        
        // Apply conflicts to working directory
        for (ConflictInfo conflict : conflicts) {
            applyConflictToWorkingDirectory(conflict);
        }
    }

    private void performFastForwardMerge(String targetCommit, String branchName) throws Exception {
        // Update current branch to target commit
        String headRaw = repository.readHEADRaw();
        if (headRaw.startsWith("ref: ")) {
            String ref = headRaw.substring(5).trim();
            repository.updateRef(ref, targetCommit);
        }
        
        // Update working directory
        restoreTreeToWorkdir(targetCommit);
        
        System.out.println("Fast-forward merge to " + branchName);
    }

    private void performMergeCommit(String currentCommit, String targetCommit, String branchName) throws Exception {
        // Create merge commit
        createMergeCommit(currentCommit, targetCommit, branchName);
        System.out.println("Merge commit created");
    }

    private boolean isFastForward(String currentCommit, String targetCommit) throws Exception {
        // Check if current commit is ancestor of target commit
        Set<String> targetAncestors = getAllAncestors(targetCommit);
        return targetAncestors.contains(currentCommit);
    }

    private String findCommonAncestor(String commit1, String commit2) throws Exception {
        Set<String> ancestors1 = getAllAncestors(commit1);
        Set<String> ancestors2 = getAllAncestors(commit2);
        
        // Find the latest common ancestor
        for (String ancestor : ancestors1) {
            if (ancestors2.contains(ancestor)) {
                return ancestor;
            }
        }
        
        return null;
    }

    private Set<String> getAllAncestors(String commitHash) throws Exception {
        Set<String> ancestors = new HashSet<>();
        Set<String> toVisit = new HashSet<>();
        toVisit.add(commitHash);
        
        while (!toVisit.isEmpty()) {
            String current = toVisit.iterator().next();
            toVisit.remove(current);
            
            if (ancestors.contains(current)) continue;
            ancestors.add(current);
            
            Optional<byte[]> raw = repository.readObjectRaw(current);
            if (raw.isEmpty()) continue;
            
            String content = new String(raw.get(), java.nio.charset.StandardCharsets.UTF_8);
            int nul = content.indexOf('\0');
            if (nul >= 0) content = content.substring(nul + 1);
            
            CommitObj commit = CommitObj.fromContent(content);
            toVisit.addAll(commit.getParents().stream().map(Hash::getValue).collect(Collectors.toList()));
        }
        
        return ancestors;
    }

    private ConflictInfo detectFileConflict(String filePath, String currentCommit, 
                                          String targetCommit, String ancestorCommit) throws Exception {
        String currentContent = getFileContentInCommit(filePath, currentCommit);
        String targetContent = getFileContentInCommit(filePath, targetCommit);
        String ancestorContent = ancestorCommit != null ? getFileContentInCommit(filePath, ancestorCommit) : "";
        
        // Check for different types of conflicts
        if (!currentContent.equals(targetContent)) {
            if (ancestorContent.isEmpty()) {
                // File didn't exist in ancestor
                if (currentContent.isEmpty() || targetContent.isEmpty()) {
                    // Only one side has the file, no conflict
                    return null;
                } else {
                    // Both sides added the file with different content
                    return new ConflictInfo(filePath, "", currentContent, targetContent, 
                                          ConflictInfo.ConflictType.ADDED_MODIFIED);
                }
            } else if (currentContent.equals(ancestorContent) && !targetContent.equals(ancestorContent)) {
                // Only target side modified
                return null; // No conflict, can be merged
            } else if (!currentContent.equals(ancestorContent) && targetContent.equals(ancestorContent)) {
                // Only current side modified
                return null; // No conflict, can be merged
            } else if (!currentContent.equals(ancestorContent) && !targetContent.equals(ancestorContent)) {
                // Both sides modified
                return new ConflictInfo(filePath, ancestorContent, currentContent, targetContent,
                                      ConflictInfo.ConflictType.BOTH_MODIFIED);
            }
        }
        
        return null;
    }

    private void applyConflictToWorkingDirectory(ConflictInfo conflict) throws Exception {
        Path file = repository.getWorkDir().resolve(conflict.getFilePath());
        Files.createDirectories(file.getParent() != null ? file.getParent() : repository.getWorkDir());
        
        String conflictContent = conflict.getConflictMarkers();
        Files.write(file, conflictContent.getBytes());
    }

    private void resolveWithOurs(String filePath) throws Exception {
        Optional<String> currentCommit = repository.readHEAD();
        if (currentCommit.isPresent() && !currentCommit.get().isEmpty()) {
            String content = getFileContentInCommit(filePath, currentCommit.get());
            if (content != null) {
                Path file = repository.getWorkDir().resolve(filePath);
                Files.write(file, content.getBytes());
            }
        }
    }

    private void resolveWithTheirs(String filePath) throws Exception {
        MergeState mergeState = getMergeState();
        if (mergeState != null) {
            String content = getFileContentInCommit(filePath, mergeState.getMergeHead());
            if (content != null) {
                Path file = repository.getWorkDir().resolve(filePath);
                Files.write(file, content.getBytes());
            }
        }
    }

    private List<String> getConflictedFilesFromWorkingDirectory() throws Exception {
        List<String> conflictedFiles = new ArrayList<>();
        
        Files.walk(repository.getWorkDir())
                .filter(Files::isRegularFile)
                .filter(p -> !p.startsWith(repository.getDhaDir()))
                .forEach(file -> {
                    try {
                        String content = Files.readString(file);
                        if (content.contains("<<<<<<< HEAD") && content.contains("=======") && content.contains(">>>>>>>")) {
                            String relativePath = repository.getWorkDir().relativize(file).toString().replace("\\", "/");
                            conflictedFiles.add(relativePath);
                        }
                    } catch (IOException e) {
                        // Ignore files that can't be read
                    }
                });
        
        return conflictedFiles;
    }

    private void createMergeCommit(String currentCommit, String targetCommit, String branchName) throws Exception {
        // Create merge commit with both parents
        List<String> parents = new ArrayList<>();
        parents.add(currentCommit);
        parents.add(targetCommit);
        
        // Use current commit's tree (or staged changes if any)
        Map<String, String> index = repository.readIndex();
        String treeHash;
        
        if (index.isEmpty()) {
            // No staged changes, use current commit's tree
            Optional<byte[]> currentRaw = repository.readObjectRaw(currentCommit);
            if (currentRaw.isEmpty()) {
                throw new Exception("Cannot create merge commit: current commit not found");
            }
            String content = new String(currentRaw.get(), java.nio.charset.StandardCharsets.UTF_8);
            int nul = content.indexOf('\0');
            if (nul >= 0) content = content.substring(nul + 1);
            CommitObj currentCommitObj = CommitObj.fromContent(content);
            treeHash = currentCommitObj.getTreeHash().getValue();
        } else {
            // Build tree from staged changes
            treeHash = buildTreeFromIndex(index);
        }
        
        // Create merge commit message
        String mergeMessage = "Merge branch '" + branchName + "'";
        
        // Create commit
        List<Hash> parentHashes = parents.stream().map(Hash::of).collect(Collectors.toList());
        CommitObj mergeCommit = new CommitObj(Hash.of(treeHash), parentHashes, System.getProperty("user.name", "unknown"),
                                            Instant.now().getEpochSecond(), mergeMessage);
        String mergeCommitHash = repository.writeObject(mergeCommit.getStorageBytes());
        
        // Update current branch ref
        String headRaw = repository.readHEADRaw();
        if (headRaw.startsWith("ref: ")) {
            String ref = headRaw.substring(5).trim();
            repository.updateRef(ref, mergeCommitHash);
        }
        
        // Clear index
        repository.writeIndex(new HashMap<>());
    }

    private String buildTreeFromIndex(Map<String, String> index) throws Exception {
        // Implementation similar to existing buildTreeFromIndex method
        class Entry { String type, hash; Entry(String t, String h){type=t;hash=h;} }
        Map<String, Map<String, Entry>> dirs = new HashMap<>();
        
        for (Map.Entry<String, String> e : index.entrySet()) {
            String path = e.getKey();
            String hash = e.getValue();
            java.nio.file.Path p = java.nio.file.Paths.get(path);
            java.nio.file.Path parent = p.getParent();
            String parentKey = parent == null ? "" : parent.toString().replace("\\", "/");
            String name = p.getFileName().toString();
            dirs.computeIfAbsent(parentKey, k -> new HashMap<>()).put(name, new Entry("blob", hash));
            
            // ensure all ancestor dirs exist
            java.nio.file.Path cur = parent;
            while (cur != null) {
                String curKey = cur.toString().replace("\\","/");
                dirs.computeIfAbsent(curKey, k -> new HashMap<>());
                cur = cur.getParent();
            }
            dirs.computeIfAbsent("", k -> new HashMap<>());
        }
        
        // Process directories bottom-up
        List<String> dirKeys = new ArrayList<>(dirs.keySet());
        dirKeys.sort(Comparator.comparingInt((String s) -> s.isEmpty() ? 0 : s.split("/").length).reversed());
        
        Map<String, String> dirHash = new HashMap<>();
        for (String dir : dirKeys) {
            Map<String, Entry> contents = dirs.get(dir);
            Map<String, Entry> finalContents = new HashMap<>(contents);
            
            for (String possibleSub : new ArrayList<>(contents.keySet())) {
                String subKey = dir.isEmpty() ? possibleSub : dir + "/" + possibleSub;
                if (dirHash.containsKey(subKey)) {
                    finalContents.put(possibleSub, new Entry("tree", dirHash.get(subKey)));
                }
            }
            
            // Create Tree object
            Tree t = new Tree();
            List<String> names = new ArrayList<>(finalContents.keySet());
            Collections.sort(names);
            for (String name : names) {
                Entry en = finalContents.get(name);
                t.addEntry(VegaObjectType.fromString(en.type), Hash.of(en.hash), name);
            }
            byte[] storage = t.getStorageBytes();
            String thash = repository.writeObject(storage);
            dirHash.put(dir, thash);
        }
        
        return dirHash.getOrDefault("", null);
    }

    private Set<String> getFilesInCommit(String commitHash) throws Exception {
        Optional<byte[]> raw = repository.readObjectRaw(commitHash);
        if (raw.isEmpty()) return new HashSet<>();
        
        String content = new String(raw.get(), java.nio.charset.StandardCharsets.UTF_8);
        int nul = content.indexOf('\0');
        if (nul >= 0) content = content.substring(nul + 1);
        
        CommitObj commit = CommitObj.fromContent(content);
        return getFilesInTree("", commit.getTreeHash().getValue());
    }

    private String getFileContentInCommit(String filePath, String commitHash) throws Exception {
        Optional<byte[]> raw = repository.readObjectRaw(commitHash);
        if (raw.isEmpty()) return "";
        
        String content = new String(raw.get(), java.nio.charset.StandardCharsets.UTF_8);
        int nul = content.indexOf('\0');
        if (nul >= 0) content = content.substring(nul + 1);
        
        CommitObj commit = CommitObj.fromContent(content);
        return getFileContentFromTree(filePath, commit.getTreeHash().getValue());
    }

    private String getFileContentFromTree(String filePath, String treeHash) throws Exception {
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
                Optional<byte[]> blobRaw = repository.readObjectRaw(e.getHash().getValue());
                if (blobRaw.isPresent()) {
                    String blobContent = new String(blobRaw.get(), java.nio.charset.StandardCharsets.UTF_8);
                    int blobNul = blobContent.indexOf('\0');
                    if (blobNul >= 0) blobContent = blobContent.substring(blobNul + 1);
                    return blobContent;
                }
            }
        }
        
        return "";
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

    private void restoreTreeToWorkdir(String commitHash) throws Exception {
        // Implementation similar to existing restoreTreeToWorkdir method
        Optional<byte[]> raw = repository.readObjectRaw(commitHash);
        if (raw.isEmpty()) return;
        
        String content = new String(raw.get(), java.nio.charset.StandardCharsets.UTF_8);
        int nul = content.indexOf('\0');
        if (nul >= 0) content = content.substring(nul + 1);
        CommitObj c = CommitObj.fromContent(content);
        String treeHash = c.getTreeHash().getValue();
        
        Set<String> targetFiles = getFilesInTree("", treeHash);
        Set<String> currentFiles = Files.walk(repository.getWorkDir())
                .filter(Files::isRegularFile)
                .filter(p -> !p.startsWith(repository.getDhaDir()))
                .map(p -> repository.getWorkDir().relativize(p).toString().replace("\\", "/"))
                .collect(Collectors.toSet());
        
        for (String filePath : currentFiles) {
            if (!targetFiles.contains(filePath)) {
                Path file = repository.getWorkDir().resolve(filePath);
                Files.deleteIfExists(file);
            }
        }
        
        restoreTree("", treeHash);
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
}
