package com.pw999.jsmd.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class QueueRetentionSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-10-21T03:30:00Z");

    QueueMonitor monitor = mock(QueueMonitor.class);
    QueueRetentionScheduler scheduler = new QueueRetentionScheduler(
            new QueueProperties(Duration.ZERO, 3, Duration.ofMinutes(15), Duration.ofDays(7), "0 30 3 * * *"),
            monitor, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void deletesItemsOlderThanTheRetentionPeriodBeforeNow() {
        given(monitor.deleteOlderThan(any())).willReturn(4);

        assertThat(scheduler.cleanup()).isEqualTo(4);

        verify(monitor).deleteOlderThan(Instant.parse("2026-10-14T03:30:00Z"));
    }

    @Test
    void failuresAreLoggedNotThrown() {
        given(monitor.deleteOlderThan(any())).willThrow(new IllegalStateException("db down"));

        assertThatCode(scheduler::scheduledCleanup).doesNotThrowAnyException();

        verify(monitor).deleteOlderThan(any());
    }
}
