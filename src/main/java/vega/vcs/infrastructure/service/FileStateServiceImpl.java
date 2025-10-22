package vega.vcs.infrastructure.service;

import vega.vcs.domain.model.*;
import vega.vcs.domain.model.*;
import vega.vcs.domain.repository.Repository;
import vega.vcs.domain.service.FileStateService;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * File state service implementation
 * Handles file state management and working directory state
 * Follows Single Responsibility Principle and Dependency Inversion Principle
 */
public class FileStateServiceImpl implements FileStateService {

    private final Repository repository;

    public FileStateServiceImpl(Repository repository) {
        this.repository = repository;
    }

    @Override
    public WorkingDirectoryState getWorkingDirectoryState() throws Exception {
        Map<String, String> index = repository.readIndex();
        Set<String> headFiles = getFilesFromHEAD();
        Set<String> workingFiles = getWorkingDirectoryFiles();
        
        Map<String, FileState> fileStates = new HashMap<>();
        Set<String> untrackedFiles = new HashSet<>();
        Set<String> stagedFiles = new HashSet<>();
        Set<String> modifiedFiles = new HashSet<>();
        Set<String> deletedFiles = new HashSet<>();
        Set<String> conflictedFiles = new HashSet<>();
        
        // Process files in index (staged files)
        for (Map.Entry<String, String> entry : index.entrySet()) {
            String filePath = entry.getKey();
            String stagedHash = entry.getValue();
            
            if (workingFiles.contains(filePath)) {
                // File exists in working directory
                String currentHash = getFileHash(filePath);
                String headHash = getFileHashFromHEAD(filePath);
                
                if (stagedHash.equals(currentHash)) {
                    if (stagedHash.equals(headHash)) {
                        // File is committed and unchanged
                        fileStates.put(filePath, FileState.UNMODIFIED);
                    } else {
                        // File is staged for commit
                        fileStates.put(filePath, FileState.STAGED);
                        stagedFiles.add(filePath);
                    }
                } else {
                    // File is staged but also modified in working directory
                    fileStates.put(filePath, FileState.STAGED);
                    stagedFiles.add(filePath);
                    // Also mark as modified
                    modifiedFiles.add(filePath);
                }
            } else {
                // File is staged for deletion
                if (stagedHash.isEmpty()) {
                    // File is staged for deletion
                    fileStates.put(filePath, FileState.DELETED);
                    deletedFiles.add(filePath);
                } else {
                    // File exists in index but not in working directory
                    fileStates.put(filePath, FileState.DELETED);
                    deletedFiles.add(filePath);
                }
            }
        }
        
        // Process files not in index
        for (String filePath : workingFiles) {
            if (!fileStates.containsKey(filePath)) {
                if (headFiles.contains(filePath)) {
                    // File exists in HEAD, check if modified
                    String currentHash = getFileHash(filePath);
                    String headHash = getFileHashFromHEAD(filePath);
                    
                    if (currentHash.equals(headHash)) {
                        fileStates.put(filePath, FileState.UNMODIFIED);
                    } else {
                        fileStates.put(filePath, FileState.MODIFIED);
                        modifiedFiles.add(filePath);
                    }
                } else {
                    // Completely new file
                    fileStates.put(filePath, FileState.UNTRACKED);
                    untrackedFiles.add(filePath);
                }
            }
        }
        
        // Check for files deleted from working directory
        // Only check files that exist in HEAD but not in working directory
        // and are not already staged for deletion
        for (String filePath : headFiles) {
            if (!fileStates.containsKey(filePath) && !workingFiles.contains(filePath)) {
                // Check if file is staged for deletion (empty hash in index)
                if (index.containsKey(filePath) && index.get(filePath).isEmpty()) {
                    // File is staged for deletion
                    fileStates.put(filePath, FileState.DELETED);
                    deletedFiles.add(filePath);
                } else if (!index.containsKey(filePath)) {
                    // File is not staged, so it's an unstaged deletion
                    fileStates.put(filePath, FileState.DELETED);
                    deletedFiles.add(filePath);
                }
            }
        }
        
        // Check for actual uncommitted changes
        boolean hasUncommittedChanges = !stagedFiles.isEmpty() || 
                                   !modifiedFiles.isEmpty() || 
                                   !untrackedFiles.isEmpty() || !deletedFiles.isEmpty();
        
        return new WorkingDirectoryState(fileStates, untrackedFiles, stagedFiles, 
                                       modifiedFiles, deletedFiles, conflictedFiles, 
                                       hasUncommittedChanges);
    }

    @Override
    public FileState getFileState(String filePath) throws Exception {
        WorkingDirectoryState state = getWorkingDirectoryState();
        return state.getFileState(filePath);
    }

    @Override
    public void updateFileState(String filePath, FileState state) throws Exception {
        // This would typically involve updating the index or working directory
        // Implementation depends on the specific state change
        switch (state) {
            case STAGED:
                // Add to index
                break;
            case UNTRACKED:
                // Remove from index
                break;
            case DELETED:
                // Mark for deletion
                break;
            default:
                // Other state changes
                break;
        }
    }

    @Override
    public boolean hasUncommittedChanges() throws Exception {
        WorkingDirectoryState state = getWorkingDirectoryState();
        return state.hasUncommittedChanges();
    }

    @Override
    public boolean hasStagedChanges() throws Exception {
        WorkingDirectoryState state = getWorkingDirectoryState();
        return state.hasStagedChanges();
    }

    @Override
    public boolean hasUnstagedChanges() throws Exception {
        WorkingDirectoryState state = getWorkingDirectoryState();
        return state.hasUnstagedChanges();
    }

    @Override
    public boolean isWorkingDirectoryClean() throws Exception {
        WorkingDirectoryState state = getWorkingDirectoryState();
        // Working directory is clean if there are no uncommitted changes
        return !state.hasUncommittedChanges();
    }

    @Override
    public Set<String> getFilesWithState(FileState state) throws Exception {
        WorkingDirectoryState workingState = getWorkingDirectoryState();
        return workingState.getFileStates().entrySet().stream()
                .filter(entry -> entry.getValue() == state)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public Set<String> getFilesFromHEAD() throws Exception {
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

    private Set<String> getWorkingDirectoryFiles() throws Exception {
        Path workDir = repository.getWorkDir();
        Path targetDir = workDir.resolve("target");
        return Files.walk(workDir)
                .filter(Files::isRegularFile)
                .filter(p -> !p.startsWith(repository.getDhaDir()))
                .filter(p -> !p.startsWith(targetDir))
                .map(p -> workDir.relativize(p).toString().replace("\\", "/"))
                .collect(Collectors.toSet());
    }

    private String getFileHash(String filePath) throws Exception {
        Path file = repository.getWorkDir().resolve(filePath);
        if (!Files.exists(file)) {
            return "";
        }
        
        byte[] storage = repository.blobObjectFromFile(file);
        byte[] sha = MessageDigest.getInstance("SHA-1").digest(storage);
        return repository.bytesToHex(sha);
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
}
