package cn.sduonline.invoice.service;

import cn.sduonline.invoice.mapper.FileObjectMapper;
import cn.sduonline.invoice.storage.ObjectStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileOrphanDeletionService {
    private final FileObjectMapper fileMapper;
    private final ObjectStorage objectStorage;

    public FileOrphanDeletionService(FileObjectMapper fileMapper, ObjectStorage objectStorage) {
        this.fileMapper = fileMapper;
        this.objectStorage = objectStorage;
    }

    @Transactional
    public boolean deleteIfStillExpired(String organizationId, String fileId) {
        var file = fileMapper.lockExpiredUnreferenced(organizationId, fileId);
        if (file == null) return false;
        objectStorage.delete(file.getStorageKey());
        return fileMapper.deleteExpiredUnreferenced(organizationId, fileId) == 1;
    }
}
