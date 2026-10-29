package com.pw999.jsmd.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class ScanSchedulerIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private final ScanLock lock = new ScanLock(new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

    private static ScanProperties properties(List<Path> directories) {
        return new ScanProperties(true, directories, Duration.ofMinutes(5), Duration.ofSeconds(5));
    }

    @Test
    void secondConcurrentAttemptSkipsWhileLockIsHeld() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        ScanTask task = () -> {
            runs.incrementAndGet();
            started.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        ScanScheduler first = new ScanScheduler(properties(List.of(Path.of("/data"))), lock, task);
        ScanScheduler second = new ScanScheduler(properties(List.of(Path.of("/data"))), lock, task);

        CompletableFuture<Boolean> running = CompletableFuture.supplyAsync(first::scan);
        assertThat(started.await(30, TimeUnit.SECONDS)).isTrue();

        assertThat(second.scan()).isFalse();
        assertThat(runs).hasValue(1);

        release.countDown();
        assertThat(running.get(30, TimeUnit.SECONDS)).isTrue();

        // the lock is released afterwards
        assertThat(second.scan()).isTrue();
        assertThat(runs).hasValue(2);
    }

    @Test
    void noDirectoriesPerformsNoScan() {
        AtomicInteger runs = new AtomicInteger();
        ScanScheduler scheduler = new ScanScheduler(properties(List.of()), lock, runs::incrementAndGet);

        assertThat(scheduler.scan()).isFalse();
        assertThat(runs).hasValue(0);
    }

    @Test
    void disabledPerformsNoScan() {
        AtomicInteger runs = new AtomicInteger();
        ScanProperties disabled = new ScanProperties(false, List.of(Path.of("/data")), Duration.ofMinutes(5),
                Duration.ofSeconds(5));

        assertThat(new ScanScheduler(disabled, lock, runs::incrementAndGet).scan()).isFalse();
        assertThat(runs).hasValue(0);
    }

    @Test
    void failingScanReleasesTheLock() {
        ScanScheduler failing = new ScanScheduler(properties(List.of(Path.of("/data"))), lock, () -> {
            throw new IllegalStateException("boom");
        });
        failing.scheduledScan();

        AtomicInteger runs = new AtomicInteger();
        assertThat(new ScanScheduler(properties(List.of(Path.of("/data"))), lock, runs::incrementAndGet).scan()).isTrue();
        assertThat(runs).hasValue(1);
    }
}
