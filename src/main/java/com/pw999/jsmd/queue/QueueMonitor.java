package com.pw999.jsmd.queue;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Inspects the work queues and removes finished items, for the Queue page.
 *
 * <p>Reads run in a read-only transaction at {@code READ UNCOMMITTED} and never take row locks, so monitoring cannot
 * delay {@link WorkQueue}. PostgreSQL does not implement that level and treats it as {@code READ COMMITTED}; with MVCC
 * plain reads neither block nor are blocked by writers, which is what matters here.
 */
@Service
public class QueueMonitor {

    private final JdbcClient jdbc;
    private final TransactionTemplate reads;

    public QueueMonitor(JdbcClient jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.reads = new TransactionTemplate(transactionManager);
        this.reads.setIsolationLevel(TransactionDefinition.ISOLATION_READ_UNCOMMITTED);
        this.reads.setReadOnly(true);
    }

    /** Number of items per status for one queue. */
    public record QueueCounts(QueueName queue, long pending, long claimed, long failed, long done, long cancelled) {

        public long total() {
            return pending + claimed + failed + done + cancelled;
        }
    }

    /**
     * One queue item for display.
     *
     * @param path one known file path of the item's content, or null when the content has none
     */
    public record QueueItemRow(long id, QueueName queue, String status, int attempts, String lastError,
                               Instant createdAt, String path) {

        public boolean failed() {
            return "FAILED".equals(status);
        }
    }

    /** Status counts for every queue, including queues without items. */
    public List<QueueCounts> counts() {
        return reads.execute(tx -> {
            List<QueueCounts> counts = new ArrayList<>();
            for (QueueName queue : QueueName.values()) {
                counts.add(jdbc.sql("""
                                SELECT count(*) FILTER (WHERE status = 'PENDING') AS pending,
                                       count(*) FILTER (WHERE status = 'CLAIMED') AS claimed,
                                       count(*) FILTER (WHERE status = 'FAILED') AS failed,
                                       count(*) FILTER (WHERE status = 'DONE') AS done,
                                       count(*) FILTER (WHERE status = 'CANCELLED') AS cancelled
                                FROM work_item WHERE queue = ?
                                """)
                        .param(queue.name())
                        .query((rs, rowNum) -> new QueueCounts(queue, rs.getLong("pending"), rs.getLong("claimed"),
                                rs.getLong("failed"), rs.getLong("done"), rs.getLong("cancelled")))
                        .single());
            }
            return counts;
        });
    }

    /** Up to {@code limit} items: failed first, then claimed, pending and finished; newest first within a status. */
    public List<QueueItemRow> items(int limit) {
        return reads.execute(tx -> jdbc.sql("""
                        SELECT wi.id, wi.queue, wi.status, wi.attempts, wi.last_error, wi.created_at, loc.path
                        FROM work_item wi
                        LEFT JOIN LATERAL (SELECT min(path) AS path FROM file_location
                                           WHERE content_id = wi.content_id) loc ON true
                        ORDER BY CASE wi.status WHEN 'FAILED' THEN 0 WHEN 'CLAIMED' THEN 1 WHEN 'PENDING' THEN 2 ELSE 3 END,
                                 wi.created_at DESC, wi.id DESC
                        LIMIT ?
                        """)
                .param(limit)
                .query((rs, rowNum) -> new QueueItemRow(rs.getLong("id"), QueueName.valueOf(rs.getString("queue")),
                        rs.getString("status"), rs.getInt("attempts"), rs.getString("last_error"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()
                                .truncatedTo(ChronoUnit.SECONDS),
                        rs.getString("path")))
                .list());
    }

    /** Whether an item with this id exists, whatever its status. */
    public boolean exists(long id) {
        return Boolean.TRUE.equals(reads.execute(tx ->
                jdbc.sql("SELECT EXISTS (SELECT 1 FROM work_item WHERE id = ?)").param(id).query(Boolean.class).single()));
    }

    /** Deletes the item if, and only if, it is failed. Returns false when it is missing or not failed. */
    public boolean removeFailed(long id) {
        return jdbc.sql("DELETE FROM work_item WHERE id = ? AND status = 'FAILED'").param(id).update() > 0;
    }

    /** Deletes every failed, done and cancelled item of every queue; pending and claimed items stay. Returns the count. */
    public int purgeFinished() {
        return jdbc.sql("DELETE FROM work_item WHERE status IN ('FAILED', 'DONE', 'CANCELLED')").update();
    }

    /** Deletes every item created before the cutoff, whatever its status, and returns how many were removed. */
    public int deleteOlderThan(Instant cutoff) {
        return jdbc.sql("DELETE FROM work_item WHERE created_at < ?").param(OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC))
                .update();
    }
}
