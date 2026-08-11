package cn.sduonline.invoice.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.file-scan", name = "worker-enabled", havingValue = "true")
public class FileSecurityScanScheduler {
    private final FileSecurityScanWorker worker;

    public FileSecurityScanScheduler(FileSecurityScanWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${app.file-scan.poll-interval:5s}")
    public void poll() {
        worker.processNext();
    }
}
