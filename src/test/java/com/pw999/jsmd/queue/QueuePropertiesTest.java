package com.pw999.jsmd.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class QueuePropertiesTest {

    @Configuration
    @EnableConfigurationProperties(QueueProperties.class)
    static class Config {
    }

    @Nested
    @SpringBootTest(classes = Config.class)
    class Defaults {

        @Autowired
        QueueProperties properties;

        @Test
        void bundledDefaults() {
            assertThat(properties.retryDelay()).isEqualTo(Duration.ofMinutes(1));
            assertThat(properties.maxAttempts()).isEqualTo(5);
            assertThat(properties.claimTimeout()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.retention()).isEqualTo(Duration.ofDays(7));
            assertThat(properties.cleanupCron()).isEqualTo("0 30 3 * * *");
        }
    }

    @Nested
    @SpringBootTest(classes = Config.class, properties = {
            "JSMD_QUEUE_RETRY_DELAY=PT10S",
            "JSMD_QUEUE_MAX_ATTEMPTS=2",
            "JSMD_QUEUE_CLAIM_TIMEOUT=PT30S",
            "JSMD_QUEUE_RETENTION=P2D",
            "JSMD_QUEUE_CLEANUP_CRON=0 0 1 * * *"
    })
    class Overrides {

        @Autowired
        QueueProperties properties;

        @Test
        void environmentOverridesDefaults() {
            assertThat(properties.retryDelay()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.maxAttempts()).isEqualTo(2);
            assertThat(properties.claimTimeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.retention()).isEqualTo(Duration.ofDays(2));
            assertThat(properties.cleanupCron()).isEqualTo("0 0 1 * * *");
        }
    }

    @Test
    void rejectsZeroAttempts() {
        new ApplicationContextRunner().withUserConfiguration(Config.class)
                .withPropertyValues("jsmd.queue.retry-delay=PT1S", "jsmd.queue.max-attempts=0",
                        "jsmd.queue.claim-timeout=PT1S", "jsmd.queue.retention=P7D",
                        "jsmd.queue.cleanup-cron=0 30 3 * * *")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNonPositiveRetention() {
        for (String retention : new String[]{"PT0S", "-P1D"}) {
            new ApplicationContextRunner().withUserConfiguration(Config.class)
                    .withPropertyValues("jsmd.queue.retry-delay=PT1S", "jsmd.queue.max-attempts=1",
                            "jsmd.queue.claim-timeout=PT1S", "jsmd.queue.retention=" + retention,
                            "jsmd.queue.cleanup-cron=0 30 3 * * *")
                    .run(context -> assertThat(context).hasFailed());
        }
    }
}
