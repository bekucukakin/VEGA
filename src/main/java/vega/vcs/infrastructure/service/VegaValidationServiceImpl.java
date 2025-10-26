package vega.vcs.infrastructure.service;

import vega.vcs.domain.model.VegaObjectType;
import vega.vcs.domain.model.CommitObj;
import vega.vcs.domain.model.Tree;
import vega.vcs.domain.model.WorkingDirectoryState;
import vega.vcs.domain.model.TreeEntry;
import vega.vcs.domain.repository.Repository;
import vega.vcs.domain.service.FileStateService;
import vega.vcs.domain.service.VegaValidationService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Git validation service implementation
 * Handles pre-operation validations to ensure Git-compliant behavior
 * Follows Single Responsibility Principle and Dependency Inversion Principle
 */
public class VegaValidationServiceImpl implements VegaValidationService {

    private final Repository repository;
    private final FileStateService fileStateService;

    public VegaValidationServiceImpl(Repository repository, FileStateService fileStateService) {
        this.repository = repository;
        this.fileStateService = fileStateService;
    }

    @Override
    public void validateCheckout(String target) throws Exception {
        // Handle special cases first
        if (target.equals("HEAD")) {
            // HEAD checkout should validate working directory
            validateWorkingDirectory();
            return;
        }
        
        // Check if target exists
        if (!targetExists(target)) {
            throw new Exception("fatal: reference is not a tree: " + target);
        }
        
        // Check if target is the same as current HEAD
        Optional<String> currentHead = repository.readHEAD();
        if (currentHead.isPresent() && currentHead.get().equals(target)) {
            throw new Exception("Already on '" + target + "'");
        }
        
        // Check if working directory is clean
        validateWorkingDirectory();
    }

    @Override
    public void validateMerge(String branchName) throws Exception {
        // Check if already in a merge
        if (isMergeInProgress()) {
            throw new Exception("fatal: You have not concluded your merge (MERGE_HEAD exists)");
        }
        
        // Check if branch exists
        Optional<String> branchCommit = repository.readRef("refs/heads/" + branchName);
        if (branchCommit.isEmpty() || branchCommit.get().isEmpty()) {
            throw new Exception("fatal: branch '" + branchName + "' not found");
        }
        
        // Check if trying to merge with current branch
        Optional<String> currentHead = repository.readHEAD();
        if (currentHead.isPresent() && currentHead.get().equals(branchCommit.get())) {
            throw new Exception("Already up to date");
        }
        
        // Check if working directory is clean
        validateWorkingDirectory();
    }

    @Override
    public void validateWorkingDirectory() throws Exception {
        // Check for staged and modified files (not untracked files)
        WorkingDirectoryState state = fileStateService.getWorkingDirectoryState();
        
        // Only check for staged changes and modified files, not untracked files
        boolean hasStagedChanges = !state.getStagedFiles().isEmpty() || !state.getDeletedFiles().isEmpty();
        boolean hasModifiedFiles = !state.getModifiedFiles().isEmpty();
        
        if (hasStagedChanges || hasModifiedFiles) {
            throw new Exception("error: Your local changes would be overwritten by checkout.\n" +
                              "Please commit your changes or stash them before you switch branches.\n" +
                              "Aborting");
        }
    }

    @Override
    public void validateFileOperations() throws Exception {
        // Check if repository is initialized
        if (!Files.exists(repository.getDhaDir())) {
            throw new Exception("fatal: not a vega repository (or any of the parent directories): .vega");
        }
    }

    @Override
    public void validateBranchCreation(String branchName) throws Exception {
        // Check if branch already exists
        Optional<String> existingBranch = repository.readRef("refs/heads/" + branchName);
        if (existingBranch.isPresent()) {
            throw new Exception("fatal: A branch named '" + branchName + "' already exists");
        }
        
        // Validate branch name
        if (branchName.contains("..") || branchName.contains("~") || 
            branchName.contains("^") || branchName.contains(":") ||
            branchName.contains("?") || branchName.contains("*") ||
            branchName.contains("[") || branchName.contains("]") ||
            branchName.startsWith("-") || branchName.endsWith(".") ||
            branchName.endsWith(".lock") || branchName.contains("@{") ||
            branchName.contains("\\")) {
            throw new Exception("fatal: '" + branchName + "' is not a valid branch name");
        }
    }

    @Override
    public void validateCommit() throws Exception {
        // Check if there are staged changes
        if (!fileStateService.hasStagedChanges()) {
            throw new Exception("no changes added to commit (use \"vega add\" and/or \"vega commit -a\")");
        }
    }

    @Override
    public void validateFileAdd(String filePath) throws Exception {
        Path file = repository.getWorkDir().resolve(filePath);
        
        // Check if file exists
        if (!Files.exists(file)) {
            // Check if it's a staged deletion
            var index = repository.readIndex();
            if (index.containsKey(filePath)) {
                // File is staged for deletion, allow it
                return;
            }
            // Check if file exists in HEAD (for deletion staging)
            if (fileExistsInHEAD(filePath)) {
                // File exists in HEAD, allow staging deletion
                return;
            }
            throw new Exception("fatal: pathspec '" + filePath + "' did not match any files");
        }
        
        // Allow directories to be added (for empty directories)
        // This is handled by the FileService implementation
    }

    @Override
    public void validateFileRemove(String filePath) throws Exception {
        // Check if file is in index
        var index = repository.readIndex();
        if (!index.containsKey(filePath)) {
            throw new Exception("fatal: pathspec '" + filePath + "' did not match any files");
        }
    }

    private boolean targetExists(String target) throws Exception {
        // Check if it's a branch
        Optional<String> branchCommit = repository.readRef("refs/heads/" + target);
        if (branchCommit.isPresent()) {
            // Branch exists, even if it has no commits (orphan branch)
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

    private boolean isMergeInProgress() throws Exception {
        Path mergeHead = repository.getDhaDir().resolve("MERGE_HEAD");
        return Files.exists(mergeHead);
    }

    private Optional<String> findObjectByPrefix(String prefix) throws Exception {
        Path objs = repository.getDhaDir().resolve("objects");
        if (!Files.exists(objs)) return Optional.empty();

        try (var ds = Files.newDirectoryStream(objs)) {
            for (Path p : ds) {
                if (!Files.isDirectory(p)) continue;
                String dirName = p.getFileName().toString();
                try (var files = Files.newDirectoryStream(p)) {
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
}
