package com.pw999.jsmd.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.pw999.jsmd.queue.QueueMonitor.QueueCounts;
import com.pw999.jsmd.queue.QueueMonitor.QueueItemRow;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class QueueMonitorIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    DriverManagerDataSource dataSource;
    JdbcClient jdbc;
    List<TransactionDefinition> began;
    QueueMonitor monitor;
    WorkQueue queue;

    @BeforeEach
    void migrate() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = JdbcClient.create(dataSource);
        began = new ArrayList<>();
        monitor = new QueueMonitor(jdbc, new DataSourceTransactionManager(dataSource) {
            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
                began.add(definition);
                super.doBegin(transaction, definition);
            }
        });
        queue = new WorkQueue(jdbc, new QueueProperties(Duration.ZERO, 3, Duration.ofMinutes(15), Duration.ofDays(7), "0 30 3 * * *"));
    }

    private long content(int n) {
        return jdbc.sql("INSERT INTO content (sha256, size) VALUES (?, 1) RETURNING id")
                .param(new byte[]{(byte) n}).query(Long.class).single();
    }

    private long item(QueueName queueName, long contentId, String status) {
        return jdbc.sql("INSERT INTO work_item (queue, content_id, status) VALUES (?, ?, ?) RETURNING id")
                .param(queueName.name()).param(contentId).param(status).query(Long.class).single();
    }

    private void path(long contentId, String path) {
        jdbc.sql("INSERT INTO file_location (path, content_id, size, modified_at_ms, last_seen_scan_id) "
                        + "VALUES (?, ?, 1, 0, 1)")
                .param(path).param(contentId).update();
    }

    private long count(String status) {
        return jdbc.sql("SELECT count(*) FROM work_item WHERE status = ?").param(status).query(Long.class).single();
    }

    private QueueCounts counts(QueueName name) {
        return monitor.counts().stream().filter(c -> c.queue() == name).findFirst().orElseThrow();
    }

    @Test
    void countsPerQueueAndStatusIncludingEmptyQueues() {
        assertThat(monitor.counts()).hasSize(2).allSatisfy(c -> assertThat(c.total()).isZero());

        item(QueueName.PROCESS, content(1), "PENDING");
        item(QueueName.PROCESS, content(2), "PENDING");
        item(QueueName.PROCESS, content(3), "CLAIMED");
        item(QueueName.PROCESS, content(4), "FAILED");
        item(QueueName.PROCESS, content(5), "DONE");
        item(QueueName.DELETE, content(6), "CANCELLED");
        item(QueueName.DELETE, content(7), "FAILED");

        assertThat(counts(QueueName.PROCESS)).isEqualTo(new QueueCounts(QueueName.PROCESS, 2, 1, 1, 1, 0));
        assertThat(counts(QueueName.DELETE)).isEqualTo(new QueueCounts(QueueName.DELETE, 0, 0, 1, 0, 1));
    }

    @Test
    void itemsAreOrderedFailedClaimedPendingThenFinishedAndLimited() {
        long done = item(QueueName.PROCESS, content(1), "DONE");
        long pending = item(QueueName.PROCESS, content(2), "PENDING");
        long claimed = item(QueueName.PROCESS, content(3), "CLAIMED");
        long failed = item(QueueName.DELETE, content(4), "FAILED");

        assertThat(monitor.items(10)).extracting(QueueItemRow::id).containsExactly(failed, claimed, pending, done);
        assertThat(monitor.items(2)).extracting(QueueItemRow::id).containsExactly(failed, claimed);
    }

    @Test
    void itemShowsAttemptsErrorAndAKnownPathOrNone() {
        long withPaths = content(1);
        path(withPaths, "/b/second.pdf");
        path(withPaths, "/a/first.pdf");
        long failed = item(QueueName.PROCESS, withPaths, "FAILED");
        jdbc.sql("UPDATE work_item SET attempts = 3, last_error = 'boom <b>' WHERE id = ?").param(failed).update();
        long orphan = item(QueueName.DELETE, content(2), "PENDING");

        List<QueueItemRow> rows = monitor.items(10);

        QueueItemRow first = rows.get(0);
        assertThat(first.id()).isEqualTo(failed);
        assertThat(first.queue()).isEqualTo(QueueName.PROCESS);
        assertThat(first.status()).isEqualTo("FAILED");
        assertThat(first.failed()).isTrue();
        assertThat(first.attempts()).isEqualTo(3);
        assertThat(first.lastError()).isEqualTo("boom <b>");
        assertThat(first.createdAt()).isNotNull();
        assertThat(first.path()).isEqualTo("/a/first.pdf");
        assertThat(rows.get(1).id()).isEqualTo(orphan);
        assertThat(rows.get(1).path()).isNull();
        assertThat(rows.get(1).failed()).isFalse();
    }

    @Test
    void removeFailedDeletesOnlyThatFailedItem() {
        long failed = item(QueueName.PROCESS, content(1), "FAILED");
        long otherFailed = item(QueueName.PROCESS, content(2), "FAILED");
        long pending = item(QueueName.PROCESS, content(3), "PENDING");
        long claimed = item(QueueName.PROCESS, content(4), "CLAIMED");
        long done = item(QueueName.PROCESS, content(5), "DONE");

        assertThat(monitor.removeFailed(failed)).isTrue();
        assertThat(monitor.removeFailed(failed)).isFalse();
        assertThat(monitor.removeFailed(pending)).isFalse();
        assertThat(monitor.removeFailed(claimed)).isFalse();
        assertThat(monitor.removeFailed(done)).isFalse();
        assertThat(monitor.removeFailed(999_999)).isFalse();

        assertThat(monitor.items(10)).extracting(QueueItemRow::id)
                .containsExactlyInAnyOrder(otherFailed, pending, claimed, done);
    }

    @Test
    void existsReportsAnyStatus() {
        long pending = item(QueueName.PROCESS, content(1), "PENDING");

        assertThat(monitor.exists(pending)).isTrue();
        assertThat(monitor.exists(999_999)).isFalse();
    }

    @Test
    void purgeRemovesFailedDoneAndCancelledItemsOnly() {
        item(QueueName.PROCESS, content(1), "FAILED");
        item(QueueName.DELETE, content(2), "FAILED");
        item(QueueName.PROCESS, content(3), "PENDING");
        item(QueueName.PROCESS, content(4), "CLAIMED");
        item(QueueName.PROCESS, content(5), "DONE");
        item(QueueName.DELETE, content(6), "CANCELLED");

        assertThat(monitor.purgeFinished()).isEqualTo(4);

        assertThat(count("FAILED")).isZero();
        assertThat(count("DONE")).isZero();
        assertThat(count("CANCELLED")).isZero();
        assertThat(count("PENDING")).isEqualTo(1);
        assertThat(count("CLAIMED")).isEqualTo(1);
        assertThat(monitor.purgeFinished()).isZero();
    }

    @Test
    void deleteOlderThanRemovesOldItemsOfEveryStatusAndKeepsRecentOnes() {
        Instant now = Instant.parse("2026-10-21T12:00:00Z");
        int n = 0;
        List<Long> recent = new ArrayList<>();
        for (String status : List.of("PENDING", "CLAIMED", "FAILED", "DONE", "CANCELLED")) {
            long old = item(QueueName.PROCESS, content(++n), status);
            jdbc.sql("UPDATE work_item SET created_at = ? WHERE id = ?")
                    .param(OffsetDateTime.ofInstant(now.minus(Duration.ofDays(8)), ZoneOffset.UTC)).param(old).update();
            long fresh = item(QueueName.PROCESS, content(++n), status);
            jdbc.sql("UPDATE work_item SET created_at = ? WHERE id = ?")
                    .param(OffsetDateTime.ofInstant(now.minus(Duration.ofDays(6)), ZoneOffset.UTC)).param(fresh).update();
            recent.add(fresh);
        }

        assertThat(monitor.deleteOlderThan(now.minus(Duration.ofDays(7)))).isEqualTo(5);

        assertThat(monitor.items(20)).extracting(QueueItemRow::id).containsExactlyInAnyOrderElementsOf(recent);
        assertThat(monitor.deleteOlderThan(now.minus(Duration.ofDays(7)))).isZero();
    }

    @Test
    void contentCanBeEnqueuedAgainAfterRemoval() {
        long content = content(1);
        long failed = item(QueueName.PROCESS, content, "FAILED");

        assertThat(monitor.removeFailed(failed)).isTrue();

        assertThat(queue.enqueue(QueueName.PROCESS, content)).isTrue();
        assertThat(count("PENDING")).isEqualTo(1);
    }

    @Test
    void readsRequestReadUncommittedReadOnlyTransactions() {
        monitor.counts();
        monitor.items(5);
        monitor.exists(1);

        assertThat(began).hasSize(3).allSatisfy(definition -> {
            assertThat(definition.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_READ_UNCOMMITTED);
            assertThat(definition.isReadOnly()).isTrue();
        });
    }

    @Test
    void inspectionDoesNotWaitForTransactionsHoldingRowLocksAndDoesNotDelayClaims() throws Exception {
        long locked = item(QueueName.PROCESS, content(1), "PENDING");
        long other = item(QueueName.PROCESS, content(2), "PENDING");
        long free = item(QueueName.PROCESS, content(3), "PENDING");

        try (Connection tx = dataSource.getConnection()) {
            tx.setAutoCommit(false);
            try (var update = tx.prepareStatement("UPDATE work_item SET status = 'CLAIMED', attempts = 1 WHERE id = ?")) {
                update.setLong(1, locked);
                update.executeUpdate();
            }
            try (var lock = tx.prepareStatement("SELECT id FROM work_item WHERE id = ? FOR UPDATE")) {
                lock.setLong(1, other);
                lock.executeQuery().close();
            }

            CompletableFuture<Object> inspect = CompletableFuture.supplyAsync(() -> {
                monitor.counts();
                monitor.items(10);
                return monitor.exists(locked);
            });
            assertThat(inspect.get(10, TimeUnit.SECONDS)).isEqualTo(true);

            // The monitor holds nothing: a consumer skips the locked rows and is not delayed either.
            var claim = CompletableFuture.supplyAsync(() -> queue.claim(QueueName.PROCESS));
            assertThat(claim.get(10, TimeUnit.SECONDS)).hasValueSatisfying(w -> assertThat(w.id()).isEqualTo(free));
            tx.rollback();
        }
    }
}
