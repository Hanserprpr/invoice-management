package cn.sduonline.invoice.storage;

import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Optional;

final class DisabledObjectStorage implements ObjectStorage {
    @Override
    public UploadGrant createUploadGrant(String key, String contentType, long sizeBytes, String sha256) {
        throw unavailable();
    }

    @Override
    public Optional<StoredObject> headUpload(String key) {
        throw unavailable();
    }

    @Override
    public void finalizeUpload(String key) {
        throw unavailable();
    }

    @Override
    public DownloadGrant createDownloadGrant(String key, String originalName, String contentType) {
        throw unavailable();
    }

    private BusinessException unavailable() {
        return new BusinessException(BizCode.OBJECT_STORAGE_NOT_CONFIGURED,
                HttpStatus.SERVICE_UNAVAILABLE);
    }
}
