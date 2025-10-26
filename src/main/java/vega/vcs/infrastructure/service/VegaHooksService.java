package vega.vcs.infrastructure.service;

import vega.vcs.domain.repository.Repository;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

/**
 * Git hooks service implementation
 * Manages Git hooks for repository operations
 * Follows Git's hook system conventions
 */
public class VegaHooksService {
    
    private final Repository repository;
    
    public VegaHooksService(Repository repository) {
        this.repository = repository;
    }
    
    /**
     * Executes pre-commit hook
     */
    public boolean executePreCommitHook() throws Exception {
        return executeHook("pre-commit");
    }
    
    /**
     * Executes commit-msg hook
     */
    public boolean executeCommitMsgHook(String message) throws Exception {
        return executeHook("commit-msg", message);
    }
    
    /**
     * Executes post-commit hook
     */
    public boolean executePostCommitHook(String commitHash) throws Exception {
        return executeHook("post-commit", commitHash);
    }
    
    /**
     * Executes pre-push hook
     */
    public boolean executePrePushHook(String remote, String url) throws Exception {
        return executeHook("pre-push", remote, url);
    }
    
    /**
     * Executes update hook
     */
    public boolean executeUpdateHook(String ref, String oldHash, String newHash) throws Exception {
        return executeHook("update", ref, oldHash, newHash);
    }
    
    /**
     * Creates default Git hooks
     */
    public void createDefaultHooks() throws IOException {
        Path hooksDir = repository.getDhaDir().resolve("hooks");
        Files.createDirectories(hooksDir);
        
        // Create pre-commit hook
        createPreCommitHook(hooksDir);
        
        // Create commit-msg hook
        createCommitMsgHook(hooksDir);
        
        // Create post-commit hook
        createPostCommitHook(hooksDir);
        
        // Create pre-push hook
        createPrePushHook(hooksDir);
        
        // Create update hook
        createUpdateHook(hooksDir);
        
        // Create other standard hooks
        createOtherHooks(hooksDir);
    }
    
    /**
     * Executes a hook with given name and arguments
     */
    private boolean executeHook(String hookName, String... args) throws Exception {
        Path hookFile = repository.getDhaDir().resolve("hooks").resolve(hookName);
        
        if (!Files.exists(hookFile)) {
            return true; // Hook doesn't exist, consider it passed
        }
        
        if (!Files.isExecutable(hookFile)) {
            return true; // Hook is not executable, consider it passed
        }
        
        // Prepare environment variables
        Map<String, String> env = new HashMap<>();
        env.put("GIT_DIR", repository.getDhaDir().toString());
        env.put("GIT_WORK_TREE", repository.getWorkDir().toString());
        
        // Prepare command
        List<String> command = new ArrayList<>();
        command.add(hookFile.toString());
        command.addAll(Arrays.asList(args));
        
        // Execute hook
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(repository.getWorkDir().toFile());
        pb.environment().putAll(env);
        
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        return exitCode == 0;
    }
    
    /**
     * Creates pre-commit hook
     */
    private void createPreCommitHook(Path hooksDir) throws IOException {
        String content = "#!/bin/sh\n" +
                        "# Pre-commit hook\n" +
                        "# This hook is called before a commit is made\n" +
                        "# Exit with non-zero status to abort the commit\n" +
                        "\n" +
                        "echo \"Running pre-commit hook...\"\n" +
                        "\n" +
                        "# Example: Run linting\n" +
                        "# if ! command -v lint-command >/dev/null 2>&1; then\n" +
                        "#     echo \"Lint command not found\"\n" +
                        "#     exit 1\n" +
                        "# fi\n" +
                        "\n" +
                        "# if ! lint-command; then\n" +
                        "#     echo \"Linting failed\"\n" +
                        "#     exit 1\n" +
                        "# fi\n" +
                        "\n" +
                        "echo \"Pre-commit hook passed\"\n" +
                        "exit 0\n";
        
        Path hookFile = hooksDir.resolve("pre-commit");
        Files.writeString(hookFile, content);
        makeExecutable(hookFile);
    }
    
    /**
     * Creates commit-msg hook
     */
    private void createCommitMsgHook(Path hooksDir) throws IOException {
        String content = "#!/bin/sh\n" +
                        "# Commit-msg hook\n" +
                        "# This hook is called with the commit message file as argument\n" +
                        "# Exit with non-zero status to abort the commit\n" +
                        "\n" +
                        "echo \"Running commit-msg hook...\"\n" +
                        "\n" +
                        "commit_msg_file=\"$1\"\n" +
                        "\n" +
                        "# Example: Check commit message format\n" +
                        "# if ! grep -q \"^[A-Z].*[.!]$\" \"$commit_msg_file\"; then\n" +
                        "#     echo \"Commit message must start with capital letter and end with period or exclamation mark\"\n" +
                        "#     exit 1\n" +
                        "# fi\n" +
                        "\n" +
                        "echo \"Commit message hook passed\"\n" +
                        "exit 0\n";
        
        Path hookFile = hooksDir.resolve("commit-msg");
        Files.writeString(hookFile, content);
        makeExecutable(hookFile);
    }
    
    /**
     * Creates post-commit hook
     */
    private void createPostCommitHook(Path hooksDir) throws IOException {
        String content = "#!/bin/sh\n" +
                        "# Post-commit hook\n" +
                        "# This hook is called after a commit is made\n" +
                        "# The commit hash is available in $1\n" +
                        "\n" +
                        "echo \"Running post-commit hook...\"\n" +
                        "\n" +
                        "commit_hash=\"$1\"\n" +
                        "echo \"Commit $commit_hash was created\"\n" +
                        "\n" +
                        "# Example: Send notification\n" +
                        "# curl -X POST \"https://api.example.com/commits\" -d \"hash=$commit_hash\"\n" +
                        "\n" +
                        "echo \"Post-commit hook completed\"\n" +
                        "exit 0\n";
        
        Path hookFile = hooksDir.resolve("post-commit");
        Files.writeString(hookFile, content);
        makeExecutable(hookFile);
    }
    
    /**
     * Creates pre-push hook
     */
    private void createPrePushHook(Path hooksDir) throws IOException {
        String content = "#!/bin/sh\n" +
                        "# Pre-push hook\n" +
                        "# This hook is called before a push\n" +
                        "# Exit with non-zero status to abort the push\n" +
                        "\n" +
                        "echo \"Running pre-push hook...\"\n" +
                        "\n" +
                        "remote_name=\"$1\"\n" +
                        "remote_url=\"$2\"\n" +
                        "\n" +
                        "echo \"Pushing to $remote_name at $remote_url\"\n" +
                        "\n" +
                        "# Example: Run tests before push\n" +
                        "# if ! command -v test-command >/dev/null 2>&1; then\n" +
                        "#     echo \"Test command not found\"\n" +
                        "#     exit 1\n" +
                        "# fi\n" +
                        "\n" +
                        "# if ! test-command; then\n" +
                        "#     echo \"Tests failed\"\n" +
                        "#     exit 1\n" +
                        "# fi\n" +
                        "\n" +
                        "echo \"Pre-push hook passed\"\n" +
                        "exit 0\n";
        
        Path hookFile = hooksDir.resolve("pre-push");
        Files.writeString(hookFile, content);
        makeExecutable(hookFile);
    }
    
    /**
     * Creates update hook
     */
    private void createUpdateHook(Path hooksDir) throws IOException {
        String content = "#!/bin/sh\n" +
                        "# Update hook\n" +
                        "# This hook is called when a ref is updated\n" +
                        "# Exit with non-zero status to reject the update\n" +
                        "\n" +
                        "echo \"Running update hook...\"\n" +
                        "\n" +
                        "ref_name=\"$1\"\n" +
                        "old_hash=\"$2\"\n" +
                        "new_hash=\"$3\"\n" +
                        "\n" +
                        "echo \"Updating $ref_name from $old_hash to $new_hash\"\n" +
                        "\n" +
                        "# Example: Check if update is allowed\n" +
                        "# if [ \"$ref_name\" = \"refs/heads/master\" ]; then\n" +
                        "#     echo \"Direct push to master is not allowed\"\n" +
                        "#     exit 1\n" +
                        "# fi\n" +
                        "\n" +
                        "echo \"Update hook passed\"\n" +
                        "exit 0\n";
        
        Path hookFile = hooksDir.resolve("update");
        Files.writeString(hookFile, content);
        makeExecutable(hookFile);
    }
    
    /**
     * Creates other standard Git hooks
     */
    private void createOtherHooks(Path hooksDir) throws IOException {
        // Create applypatch-msg hook
        createHook(hooksDir, "applypatch-msg", "Applypatch-msg hook");
        
        // Create pre-applypatch hook
        createHook(hooksDir, "pre-applypatch", "Pre-applypatch hook");
        
        // Create post-applypatch hook
        createHook(hooksDir, "post-applypatch", "Post-applypatch hook");
        
        // Create pre-rebase hook
        createHook(hooksDir, "pre-rebase", "Pre-rebase hook");
        
        // Create post-checkout hook
        createHook(hooksDir, "post-checkout", "Post-checkout hook");
        
        // Create post-merge hook
        createHook(hooksDir, "post-merge", "Post-merge hook");
        
        // Create pre-receive hook
        createHook(hooksDir, "pre-receive", "Pre-receive hook");
        
        // Create update hook (already created above)
        
        // Create post-receive hook
        createHook(hooksDir, "post-receive", "Post-receive hook");
        
        // Create post-update hook
        createHook(hooksDir, "post-update", "Post-update hook");
        
        // Create push-to-checkout hook
        createHook(hooksDir, "push-to-checkout", "Push-to-checkout hook");
        
        // Create pre-auto-gc hook
        createHook(hooksDir, "pre-auto-gc", "Pre-auto-gc hook");
        
        // Create post-rewrite hook
        createHook(hooksDir, "post-rewrite", "Post-rewrite hook");
    }
    
    /**
     * Creates a simple hook file
     */
    private void createHook(Path hooksDir, String hookName, String description) throws IOException {
        String content = "#!/bin/sh\n" +
                        "# " + description + "\n" +
                        "# This hook is called during " + hookName.replace("-", " ") + "\n" +
                        "\n" +
                        "echo \"Running " + hookName + " hook...\"\n" +
                        "echo \"" + description + " completed\"\n" +
                        "exit 0\n";
        
        Path hookFile = hooksDir.resolve(hookName);
        Files.writeString(hookFile, content);
        makeExecutable(hookFile);
    }
    
    /**
     * Makes a file executable
     */
    private void makeExecutable(Path file) throws IOException {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, permissions);
        } catch (UnsupportedOperationException e) {
            // On Windows, we can't set POSIX permissions
            // The file will be executable by default
        }
    }
}
