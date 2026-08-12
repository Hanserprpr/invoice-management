package cn.sduonline.invoice.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.recognition", name = "worker-enabled", havingValue = "true")
public class RecognitionScheduler {
    private final RecognitionWorker worker;

    public RecognitionScheduler(RecognitionWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${app.recognition.poll-interval:5s}")
    public void poll() {
        worker.processNext();
    }
}
