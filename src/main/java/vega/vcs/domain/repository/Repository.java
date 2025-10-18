package vega.vcs.domain.repository;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Repository interface for Vega operations
 * Follows Repository Pattern and Single Responsibility Principle
 */
public interface Repository {

    // Path operations
    Path getWorkDir();
    Path getDhaDir();

    // Initialization
    void init() throws Exception;

    // Object operations
    String writeObject(byte[] storageBytes) throws Exception;
    Optional<byte[]> readObjectRaw(String hash) throws Exception;

    // Index operations
    Map<String, String> readIndex() throws Exception;
    void writeIndex(Map<String, String> indexMap) throws Exception;

    // Reference operations
    void updateRef(String refPath, String value) throws Exception;
    Optional<String> readRef(String refPath) throws Exception;
    Optional<String> readHEAD() throws Exception;
    String readHEADRaw() throws Exception;
    void setHEADToRef(String ref) throws Exception;
    void setHEADDetached(String commitHash) throws Exception;

    // Utility operations
    byte[] blobObjectFromFile(Path file) throws Exception;
    String bytesToHex(byte[] bytes);
}
