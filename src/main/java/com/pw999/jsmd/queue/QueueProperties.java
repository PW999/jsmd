package com.pw999.jsmd.queue;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Retry and retention behaviour of the work queues.
 *
 * @param retryDelay   how long a failed item waits before it is offered again
 * @param maxAttempts  claims allowed per item before a failure becomes permanent
 * @param claimTimeout how long a claim may stay incomplete before the item is offered again
 * @param retention    how long after creation an item is deleted by the daily cleanup, whatever its status
 * @param cleanupCron  Spring cron expression for the daily cleanup
 */
@Validated
@ConfigurationProperties("jsmd.queue")
public record QueueProperties(@NotNull Duration retryDelay, @Min(1) int maxAttempts, @NotNull Duration claimTimeout,
                              @NotNull Duration retention, @NotBlank String cleanupCron) {

    @AssertTrue(message = "retention must be positive")
    boolean isRetentionPositive() {
        return retention == null || retention.isPositive();
    }
}
