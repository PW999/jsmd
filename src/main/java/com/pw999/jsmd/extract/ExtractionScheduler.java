package com.pw999.jsmd.extract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the PROCESS queue on a fixed-delay schedule.
 *
 * <p>Calls {@link ExtractionService#processNext()} in a loop until the queue is empty or extraction
 * is disabled. Because {@link com.pw999.jsmd.queue.WorkQueue#claim} uses {@code FOR UPDATE SKIP LOCKED},
 * multiple running instances are safe without an additional distributed lock.
 */
@Component
@EnableConfigurationProperties(ExtractProperties.class)
public class ExtractionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExtractionScheduler.class);

    private final ExtractProperties properties;
    private final ExtractionService extractionService;

    public ExtractionScheduler(ExtractProperties properties, ExtractionService extractionService) {
        this.properties = properties;
        this.extractionService = extractionService;
    }

    @Scheduled(fixedDelayString = "${jsmd.extract.poll-interval}")
    void scheduledExtraction() {
        try {
            drainQueue();
        } catch (RuntimeException e) {
            log.error("Extraction scheduler error", e);
        }
    }

    /**
     * Drains the queue until it is empty or extraction is disabled.
     *
     * @return number of items processed
     */
    int drainQueue() {
        if (!properties.enabled()) {
            return 0;
        }
        int count = 0;
        while (extractionService.processNext()) {
            count++;
        }
        return count;
    }
}
