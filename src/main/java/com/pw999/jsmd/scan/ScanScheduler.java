package com.pw999.jsmd.scan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Starts a scan every {@code jsmd.scan.interval} after the previous one finished, so scans never overlap within this
 * instance; the {@link ScanLock} keeps other instances out as well.
 */
@Component
@EnableConfigurationProperties(ScanProperties.class)
public class ScanScheduler {

    private static final Logger log = LoggerFactory.getLogger(ScanScheduler.class);

    private final ScanProperties properties;
    private final ScanLock lock;
    private final ScanTask task;

    public ScanScheduler(ScanProperties properties, ScanLock lock, ScanTask task) {
        this.properties = properties;
        this.lock = lock;
        this.task = task;
    }

    @Scheduled(fixedDelayString = "${jsmd.scan.interval}")
    void scheduledScan() {
        try {
            scan();
        } catch (RuntimeException e) {
            log.error("Scan failed", e);
        }
    }

    /**
     * @return true if a scan ran; false if scanning is off, no directories are configured, or another scan holds the lock
     */
    boolean scan() {
        if (!properties.active()) {
            return false;
        }
        boolean ran = lock.runExclusively(task::run);
        if (!ran) {
            log.info("Another scan is running; skipping this one");
        }
        return ran;
    }
}
