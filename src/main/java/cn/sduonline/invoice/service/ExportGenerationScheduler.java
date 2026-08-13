package cn.sduonline.invoice.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.export.worker-enabled", havingValue = "true")
public class ExportGenerationScheduler {
    private final ExportGenerationWorker worker;

    public ExportGenerationScheduler(ExportGenerationWorker worker) { this.worker = worker; }

    @Scheduled(fixedDelayString = "${app.export.poll-interval:5s}")
    public void process() {
        while (worker.processNext()) { }
    }
}
