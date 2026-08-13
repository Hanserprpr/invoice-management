package cn.sduonline.invoice.storage;

import java.time.Instant;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ObjectStorage {
    UploadGrant createUploadGrant(String key, String contentType, long sizeBytes, String sha256);

    Optional<StoredObject> headUpload(String key);

    void finalizeUpload(String key);

    void downloadTo(String key, Path target);

    default void uploadFrom(String key, Path source, String contentType, String sha256) {
        throw new UnsupportedOperationException("server-side upload is not supported");
    }

    void delete(String key);

    DownloadGrant createDownloadGrant(String key, String originalName, String contentType);

    record UploadGrant(String url, Map<String, List<String>> requiredHeaders, Instant expiresAt) {
    }

    record DownloadGrant(String url, Instant expiresAt) {
    }

    record StoredObject(long sizeBytes, String contentType, Map<String, String> metadata) {
    }
}
