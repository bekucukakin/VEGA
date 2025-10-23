package vega.vcs.infrastructure.service;

import vega.vcs.domain.model.*;
import vega.vcs.domain.repository.Repository;
import vega.vcs.domain.service.CommitService;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

/**
 * Commit service implementation
 * Handles commit operations and history
 * Follows Single Responsibility Principle and Dependency Inversion Principle
 */
public class CommitServiceImpl implements CommitService {

    private final Repository repository;
    private final String defaultAuthor;

    public CommitServiceImpl(Repository repository, String defaultAuthor) {
        this.repository = repository;
        this.defaultAuthor = defaultAuthor;
    }

    @Override
    public void createCommit(String message) throws Exception {
        Map<String, String> index = repository.readIndex();
        if (index.isEmpty()) {
            System.out.println("nothing to commit");
            return;
        }
        String treeHash = buildTreeFromIndex(index);
        // get parent from HEAD (if exists)
        Optional<String> headCommit = repository.readHEAD();
        List<Hash> parents = new ArrayList<>();
        headCommit.ifPresent(hash -> parents.add(Hash.of(hash)));
        CommitObj commit = new CommitObj(Hash.of(treeHash), parents, defaultAuthor, Instant.now().getEpochSecond(), message);
        String commitHash = repository.writeObject(commit.getStorageBytes());
        // update current branch ref if HEAD is a ref
        String headRaw = repository.readHEADRaw();
        if (headRaw.startsWith("ref: ")) {
            String ref = headRaw.substring(5).trim();
            repository.updateRef(ref, commitHash);
        } else {
            // detached: set HEAD to commit hash
            repository.setHEADDetached(commitHash);
        }
        // clear index (staging area)
        repository.writeIndex(new HashMap<>());
        System.out.println("Committed: " + commitHash);
    }

    @Override
    public void showLog() throws Exception {
        // 1. Tüm commit'leri bul ve tarihe göre sırala
        List<CommitInfo> allCommits = findAllCommits();
        
        if (allCommits.isEmpty()) {
            System.out.println("No commits");
            return;
        }
        
        // 2. Current HEAD commit'ini al
        Optional<String> head = repository.readHEAD();
        String currentCommitHash = head.orElse("");
        
        // 3. Tüm commit'leri göster
        for (CommitInfo commitInfo : allCommits) {
            String commitHash = commitInfo.hash;
            CommitObj commit = commitInfo.commit;
            
            // 4. Current commit'i belirgin şekilde göster
            if (commitHash.equals(currentCommitHash)) {
                System.out.println("commit " + commitHash + " (HEAD -> current)");
            } else {
                System.out.println("commit " + commitHash);
            }
            
            System.out.println("Author: " + commit.getAuthor());
            System.out.println("Date: " + Instant.ofEpochSecond(commit.getTimestamp()).toString());
            System.out.println();
            System.out.println("    " + commit.getMessage());
            System.out.println();
        }
    }
    
    /**
     * Tüm commit'leri bulur ve tarihe göre sıralar
     */
    private List<CommitInfo> findAllCommits() throws Exception {
        List<CommitInfo> commits = new ArrayList<>();
        Path objectsDir = repository.getDhaDir().resolve("objects");
        
        if (!Files.exists(objectsDir)) {
            return commits;
        }
        
        // Objects dizinindeki tüm dosyaları tara
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(objectsDir)) {
            for (Path dir : dirs) {
                if (!Files.isDirectory(dir)) continue;
                
                try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
                    for (Path file : files) {
                        String hash = dir.getFileName().toString() + file.getFileName().toString();
                        
                        // Commit objesi mi kontrol et
                        Optional<byte[]> raw = repository.readObjectRaw(hash);
                        if (raw.isPresent()) {
                            String content = new String(raw.get(), java.nio.charset.StandardCharsets.UTF_8);
                            
                            // Commit objesi mi kontrol et (header "commit" ile başlıyor mu)
                            if (content.startsWith("commit ")) {
                                int nul = content.indexOf('\0');
                                if (nul >= 0) {
                                    String commitContent = content.substring(nul + 1);
                                    try {
                                        CommitObj commit = CommitObj.fromContent(commitContent);
                                        commits.add(new CommitInfo(hash, commit));
                                    } catch (Exception e) {
                                        // Geçersiz commit objesi, atla
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Tarihe göre sırala (en yeni önce)
        // Eğer timestamp aynıysa, hash'e göre sırala (deterministic sıralama)
        commits.sort((a, b) -> {
            int timeCompare = Long.compare(b.commit.getTimestamp(), a.commit.getTimestamp());
            if (timeCompare != 0) {
                return timeCompare;
            }
            // Aynı timestamp ise hash'e göre sırala
            return a.hash.compareTo(b.hash);
        });
        
        return commits;
    }
    
    /**
     * Commit bilgilerini tutan inner class
     */
    private static class CommitInfo {
        final String hash;
        final CommitObj commit;
        
        CommitInfo(String hash, CommitObj commit) {
            this.hash = hash;
            this.commit = commit;
        }
    }

    private String buildTreeFromIndex(Map<String, String> index) throws Exception {
        Optional<String> headCommit = repository.readHEAD();
        Map<String, String> previousTree = new HashMap<>();

        if (headCommit.isPresent() && !headCommit.get().isEmpty()) {
            previousTree = getFilesFromCommit(headCommit.get());
        } else {
        }

        // Merge current index with previous commit's files
        Map<String, String> mergedIndex = new HashMap<>(previousTree);
        for (Map.Entry<String, String> entry : index.entrySet()) {
            String filePath = entry.getKey();
            String hash = entry.getValue();

            if (hash.isEmpty()) {
                mergedIndex.remove(filePath);
            } else {
                mergedIndex.put(filePath, hash);
            }
        }

        return buildTreeFromMergedIndex(mergedIndex);
    }


    private Map<String, String> getFilesFromCommit(String commitHash) throws Exception {
        Map<String, String> files = new HashMap<>();
        Optional<byte[]> raw = repository.readObjectRaw(commitHash);
        if (raw.isPresent()) {
            String content = new String(raw.get(), java.nio.charset.StandardCharsets.UTF_8);
            int nul = content.indexOf('\0');
            if (nul >= 0) content = content.substring(nul + 1);

            CommitObj commit = CommitObj.fromContent(content);
            files = getFilesInTree("", commit.getTreeHash().getValue());
        } else {
        }
        return files;
    }

// -----------------------------------------------------------------------

    private Map<String, String> getFilesInTree(String prefix, String treeHash) throws Exception {
        Map<String, String> files = new HashMap<>();
        Optional<byte[]> raw = repository.readObjectRaw(treeHash);
        if (raw.isEmpty()) {
            return files;
        }

        String content = new String(raw.get(), java.nio.charset.StandardCharsets.UTF_8);
        int nul = content.indexOf('\0');
        if (nul >= 0) content = content.substring(nul + 1);
        Tree t = Tree.fromContent(content);

        for (TreeEntry e : t.getEntries()) {
            String path = prefix.isEmpty() ? e.getName() : prefix + "/" + e.getName();
            if (e.getType() == VegaObjectType.BLOB) {
                files.put(path, e.getHash().getValue());
            } else if (e.getType() == VegaObjectType.TREE) {
                files.putAll(getFilesInTree(path, e.getHash().getValue()));
            }
        }
        return files;
    }

// -----------------------------------------------------------------------

    private String buildTreeFromMergedIndex(Map<String, String> mergedIndex) throws Exception {
        class Entry { String type, hash; Entry(String t, String h){type=t;hash=h;} }
        Map<String, Map<String, Entry>> dirs = new HashMap<>();

        // --- First pass: Collect all files and ensure directories exist
        for (Map.Entry<String, String> e : mergedIndex.entrySet()) {
            String path = e.getKey();
            String hash = e.getValue();

            Path p = Paths.get(path);
            Path parent = p.getParent();
            String parentKey = parent == null ? "" : parent.toString().replace("\\", "/");
            String name = p.getFileName().toString();

            dirs.computeIfAbsent(parentKey, k -> new HashMap<>()).put(name, new Entry("blob", hash));

            // Ensure all ancestor directories exist
            Path cur = parent;
            while (cur != null) {
                String curKey = cur.toString().replace("\\", "/");
                dirs.computeIfAbsent(curKey, k -> new HashMap<>());
                cur = cur.getParent();
            }
        }

        dirs.computeIfAbsent("", k -> new HashMap<>());

        // --- Second pass: Create trees bottom-up
        List<String> dirKeys = new ArrayList<>(dirs.keySet());
        dirKeys.sort(Comparator.comparingInt((String s) -> s.isEmpty() ? 0 : s.split("/").length).reversed());

        Map<String, String> dirHash = new HashMap<>();
        for (String dir : dirKeys) {
            Map<String, Entry> contents = dirs.get(dir);
            Map<String, Entry> finalContents = new HashMap<>(contents);

            // Replace subdirectory entries with their tree hashes
            for (String possibleSub : new ArrayList<>(contents.keySet())) {
                String subKey = dir.isEmpty() ? possibleSub : dir + "/" + possibleSub;
                if (dirHash.containsKey(subKey)) {
                    finalContents.put(possibleSub, new Entry("tree", dirHash.get(subKey)));
                }
            }

            // Handle subdirectories explicitly
            for (String subDir : dirHash.keySet()) {
                if (!subDir.equals(dir) &&
                        (dir.isEmpty() ? subDir.indexOf('/') == -1
                                : subDir.startsWith(dir + "/") && subDir.indexOf('/', dir.length() + 1) == -1)) {
                    String subName = dir.isEmpty() ? subDir : subDir.substring(dir.length() + 1);
                    if (!finalContents.containsKey(subName)) {
                        finalContents.put(subName, new Entry("tree", dirHash.get(subDir)));
                    }
                }
            }

            Tree t = new Tree();
            List<String> names = new ArrayList<>(finalContents.keySet());
            Collections.sort(names);

            for (String name : names) {
                Entry en = finalContents.get(name);
                System.out.println("   🔹 " + en.type.toUpperCase() + " → " + name + " (" + en.hash + ")");
                t.addEntry(VegaObjectType.fromString(en.type), Hash.of(en.hash), name);
            }

            byte[] storage = t.getStorageBytes();
            String thash = repository.writeObject(storage);
            dirHash.put(dir, thash);
        }

        String rootHash = dirHash.get("");
        if (rootHash == null) {
            Tree emptyTree = new Tree();
            byte[] storage = emptyTree.getStorageBytes();
            rootHash = repository.writeObject(storage);
        }

        return rootHash;
    }

}
