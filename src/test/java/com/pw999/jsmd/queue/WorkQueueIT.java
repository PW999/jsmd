package com.pw999.jsmd.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class WorkQueueIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    JdbcClient jdbc;

    @BeforeEach
    void migrate() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
        jdbc = JdbcClient.create(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private WorkQueue queue(Duration retryDelay, int maxAttempts, Duration claimTimeout) {
        return new WorkQueue(jdbc, new QueueProperties(retryDelay, maxAttempts, claimTimeout, Duration.ofDays(7), "0 30 3 * * *"));
    }

    private WorkQueue queue() {
        return queue(Duration.ZERO, 3, Duration.ofMinutes(15));
    }

    private long content(int n) {
        return jdbc.sql("INSERT INTO content (sha256, size) VALUES (?, 1) RETURNING id")
                .param(new byte[]{(byte) n})
                .query(Long.class).single();
    }

    private String status(long itemId) {
        return jdbc.sql("SELECT status FROM work_item WHERE id = ?").param(itemId).query(String.class).single();
    }

    @Test
    void enqueueIgnoresSecondOpenItemForSameQueueAndContent() {
        WorkQueue q = queue();
        long c = content(1);

        assertThat(q.enqueue(QueueName.PROCESS, c)).isTrue();
        assertThat(q.enqueue(QueueName.PROCESS, c)).isFalse();

        // still suppressed while the item is claimed
        q.claim(QueueName.PROCESS).orElseThrow();
        assertThat(q.enqueue(QueueName.PROCESS, c)).isFalse();

        assertThat(jdbc.sql("SELECT count(*) FROM work_item").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void sameContentCanBeOnBothQueues() {
        WorkQueue q = queue();
        long c = content(1);

        assertThat(q.enqueue(QueueName.PROCESS, c)).isTrue();
        assertThat(q.enqueue(QueueName.DELETE, c)).isTrue();

        assertThat(q.claim(QueueName.PROCESS)).hasValueSatisfying(i -> assertThat(i.contentId()).isEqualTo(c));
        assertThat(q.claim(QueueName.DELETE)).hasValueSatisfying(i -> assertThat(i.contentId()).isEqualTo(c));
    }

    @Test
    void claimReturnsOldestFirstAndNothingWhenEmpty() {
        WorkQueue q = queue();
        assertThat(q.claim(QueueName.PROCESS)).isEmpty();

        long first = content(1);
        long second = content(2);
        q.enqueue(QueueName.PROCESS, first);
        q.enqueue(QueueName.PROCESS, second);

        assertThat(q.claim(QueueName.PROCESS).orElseThrow().contentId()).isEqualTo(first);
        assertThat(q.claim(QueueName.PROCESS).orElseThrow().contentId()).isEqualTo(second);
        assertThat(q.claim(QueueName.PROCESS)).isEmpty();
    }

    @Test
    void concurrentClaimHasExactlyOneWinner() throws Exception {
        WorkQueue q = queue();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 10; round++) {
                jdbc.sql("DELETE FROM work_item").update();
                q.enqueue(QueueName.PROCESS, content(100 + round));

                CyclicBarrier barrier = new CyclicBarrier(2);
                Callable<Optional<WorkItem>> consumer = () -> {
                    barrier.await();
                    return q.claim(QueueName.PROCESS);
                };
                List<Future<Optional<WorkItem>>> results = List.of(pool.submit(consumer), pool.submit(consumer));

                long winners = 0;
                for (Future<Optional<WorkItem>> result : results) {
                    if (result.get().isPresent()) {
                        winners++;
                    }
                }
                assertThat(winners).as("round %d", round).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void completedItemIsNoLongerAvailable() {
        WorkQueue q = queue();
        q.enqueue(QueueName.PROCESS, content(1));
        WorkItem item = q.claim(QueueName.PROCESS).orElseThrow();

        assertThat(q.complete(item.id())).isTrue();

        assertThat(status(item.id())).isEqualTo("DONE");
        assertThat(q.claim(QueueName.PROCESS)).isEmpty();
        assertThat(q.complete(item.id())).isFalse();
    }

    @Test
    void failedItemIsOfferedAgainOnlyAfterRetryDelay() {
        WorkQueue q = queue(Duration.ofHours(1), 3, Duration.ofMinutes(15));
        q.enqueue(QueueName.PROCESS, content(1));
        WorkItem item = q.claim(QueueName.PROCESS).orElseThrow();
        assertThat(item.attempts()).isEqualTo(1);

        assertThat(q.fail(item.id(), "boom")).isTrue();

        assertThat(status(item.id())).isEqualTo("PENDING");
        assertThat(q.claim(QueueName.PROCESS)).isEmpty();

        jdbc.sql("UPDATE work_item SET available_at = now() WHERE id = ?").param(item.id()).update();
        WorkItem again = q.claim(QueueName.PROCESS).orElseThrow();
        assertThat(again.id()).isEqualTo(item.id());
        assertThat(again.attempts()).isEqualTo(2);
    }

    @Test
    void itemFailsPermanentlyWhenAttemptsAreExhausted() {
        WorkQueue q = queue(Duration.ZERO, 2, Duration.ofMinutes(15));
        q.enqueue(QueueName.PROCESS, content(1));

        WorkItem first = q.claim(QueueName.PROCESS).orElseThrow();
        q.fail(first.id(), "first");
        WorkItem last = q.claim(QueueName.PROCESS).orElseThrow();
        q.fail(last.id(), "last");

        assertThat(status(last.id())).isEqualTo("FAILED");
        assertThat(q.claim(QueueName.PROCESS)).isEmpty();
        assertThat(jdbc.sql("SELECT last_error FROM work_item WHERE id = ?").param(last.id()).query(String.class).single())
                .isEqualTo("last");
    }

    @Test
    void staleClaimIsOfferedAgain() {
        WorkQueue q = queue(Duration.ZERO, 3, Duration.ofMinutes(15));
        q.enqueue(QueueName.PROCESS, content(1));
        WorkItem item = q.claim(QueueName.PROCESS).orElseThrow();

        // a fresh claim is left alone
        assertThat(q.claim(QueueName.PROCESS)).isEmpty();

        jdbc.sql("UPDATE work_item SET claimed_at = now() - interval '1 hour' WHERE id = ?").param(item.id()).update();
        WorkItem again = q.claim(QueueName.PROCESS).orElseThrow();

        assertThat(again.id()).isEqualTo(item.id());
        assertThat(again.attempts()).isEqualTo(2);
    }

    @Test
    void staleClaimOnLastAttemptFailsPermanently() {
        WorkQueue q = queue(Duration.ZERO, 1, Duration.ofMinutes(15));
        q.enqueue(QueueName.PROCESS, content(1));
        WorkItem item = q.claim(QueueName.PROCESS).orElseThrow();
        jdbc.sql("UPDATE work_item SET claimed_at = now() - interval '1 hour' WHERE id = ?").param(item.id()).update();

        assertThat(q.requeueStaleClaims()).isEqualTo(1);

        assertThat(status(item.id())).isEqualTo("FAILED");
        assertThat(q.claim(QueueName.PROCESS)).isEmpty();
    }

    @Test
    void cancelWorksBeforeClaimButNotAfter() {
        WorkQueue q = queue();
        long c1 = content(1);
        long c2 = content(2);
        q.enqueue(QueueName.DELETE, c1);
        q.enqueue(QueueName.DELETE, c2);

        assertThat(q.cancelPending(QueueName.DELETE, c1)).isTrue();
        WorkItem claimed = q.claim(QueueName.DELETE).orElseThrow();
        assertThat(claimed.contentId()).isEqualTo(c2);

        assertThat(q.cancelPending(QueueName.DELETE, c2)).isFalse();
        assertThat(status(claimed.id())).isEqualTo("CLAIMED");
        assertThat(q.claim(QueueName.DELETE)).isEmpty();
    }
}
