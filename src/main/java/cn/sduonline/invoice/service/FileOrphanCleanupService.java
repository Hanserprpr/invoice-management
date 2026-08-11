package cn.sduonline.invoice.service;

import cn.sduonline.invoice.mapper.FileObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.file-cleanup", name = "enabled", havingValue = "true")
public class FileOrphanCleanupService {
    private static final int BATCH_SIZE = 100;
    private final FileObjectMapper fileMapper;
    private final FileOrphanDeletionService deletionService;

    public FileOrphanCleanupService(FileObjectMapper fileMapper,
                                    FileOrphanDeletionService deletionService) {
        this.fileMapper = fileMapper;
        this.deletionService = deletionService;
    }

    @Scheduled(fixedDelayString = "${app.file-cleanup.interval:1h}")
    public void cleanup() {
        for (var file : fileMapper.findExpiredUnreferenced(BATCH_SIZE)) {
            deletionService.deleteIfStillExpired(file.getOrganizationId(), file.getId());
        }
    }
}
