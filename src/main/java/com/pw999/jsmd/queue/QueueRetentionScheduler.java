package com.pw999.jsmd.queue;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes queue items older than {@code jsmd.queue.retention} once a day, at {@code jsmd.queue.cleanup-cron}.
 *
 * <p>The delete is one idempotent statement, so several running instances need no coordination.
 */
@Component
@EnableConfigurationProperties(QueueProperties.class)
public class QueueRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(QueueRetentionScheduler.class);

    private final QueueProperties properties;
    private final QueueMonitor monitor;
    private final Clock clock;

    public QueueRetentionScheduler(QueueProperties properties, QueueMonitor monitor, Clock clock) {
        this.properties = properties;
        this.monitor = monitor;
        this.clock = clock;
    }

    @Scheduled(cron = "${jsmd.queue.cleanup-cron}")
    void scheduledCleanup() {
        try {
            cleanup();
        } catch (RuntimeException e) {
            log.error("Queue cleanup failed", e);
        }
    }

    /** @return number of items removed */
    int cleanup() {
        int removed = monitor.deleteOlderThan(clock.instant().minus(properties.retention()));
        log.info("Queue cleanup removed {} item(s) older than {}", removed, properties.retention());
        return removed;
    }
}
