package com.pw999.jsmd.queue;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Database-backed queues with at-least-once delivery: consumers must be idempotent. Every operation is a single
 * statement, so calls made inside a caller's transaction join it.
 */
@Service
@EnableConfigurationProperties(QueueProperties.class)
public class WorkQueue {

    private static final Logger log = LoggerFactory.getLogger(WorkQueue.class);

    private final JdbcClient jdbc;
    private final QueueProperties properties;

    public WorkQueue(JdbcClient jdbc, QueueProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    /**
     * Adds a pending item unless the content already has a pending or claimed item on that queue.
     *
     * @return true if an item was created
     */
    public boolean enqueue(QueueName queue, long contentId) {
        return jdbc.sql("INSERT INTO work_item (queue, content_id, status) VALUES (?, ?, 'PENDING') ON CONFLICT DO NOTHING")
                .param(queue.name())
                .param(contentId)
                .update() > 0;
    }

    /**
     * Claims the oldest available item, or returns empty. Claims that were never completed within the claim timeout
     * are made available again first.
     */
    public Optional<WorkItem> claim(QueueName queue) {
        requeueStaleClaims();
        return jdbc.sql("""
                        UPDATE work_item SET status = 'CLAIMED', claimed_at = now(), attempts = attempts + 1
                        WHERE id = (SELECT id FROM work_item
                                    WHERE queue = ? AND status = 'PENDING' AND available_at <= now()
                                    ORDER BY available_at, id
                                    LIMIT 1
                                    FOR UPDATE SKIP LOCKED)
                        RETURNING id, content_id, attempts
                        """)
                .param(queue.name())
                .query((rs, rowNum) -> new WorkItem(rs.getLong("id"), queue, rs.getLong("content_id"), rs.getInt("attempts")))
                .optional();
    }

    /** Marks a claimed item done. Returns false if the item is not claimed (for example its claim timed out). */
    public boolean complete(long id) {
        return jdbc.sql("UPDATE work_item SET status = 'DONE', claimed_at = NULL WHERE id = ? AND status = 'CLAIMED'")
                .param(id)
                .update() > 0;
    }

    /**
     * Marks a claimed item failed: it becomes available again after the retry delay, or is failed permanently when
     * its attempts are used up. Returns false if the item is not claimed.
     */
    public boolean fail(long id, String error) {
        return jdbc.sql("""
                        UPDATE work_item
                        SET status = CASE WHEN attempts >= ? THEN 'FAILED' ELSE 'PENDING' END,
                            available_at = now() + ? * interval '1 millisecond',
                            claimed_at = NULL,
                            last_error = ?
                        WHERE id = ? AND status = 'CLAIMED'
                        """)
                .param(properties.maxAttempts())
                .param(properties.retryDelay().toMillis())
                .param(error)
                .param(id)
                .update() > 0;
    }

    /** Cancels a pending item; an item that is already claimed is left alone. Returns true if one was cancelled. */
    public boolean cancelPending(QueueName queue, long contentId) {
        return jdbc.sql("""
                        UPDATE work_item SET status = 'CANCELLED'
                        WHERE queue = ? AND content_id = ? AND status = 'PENDING'
                        """)
                .param(queue.name())
                .param(contentId)
                .update() > 0;
    }

    /**
     * Makes claims older than the claim timeout available again, or fails them permanently when their attempts are
     * used up.
     *
     * @return the number of claims released
     */
    public int requeueStaleClaims() {
        int released = jdbc.sql("""
                        UPDATE work_item
                        SET status = CASE WHEN attempts >= ? THEN 'FAILED' ELSE 'PENDING' END,
                            available_at = now(),
                            claimed_at = NULL,
                            last_error = 'claim timed out'
                        WHERE status = 'CLAIMED' AND claimed_at < now() - ? * interval '1 millisecond'
                        """)
                .param(properties.maxAttempts())
                .param(properties.claimTimeout().toMillis())
                .update();
        if (released > 0) {
            log.warn("Released {} stale queue claim(s)", released);
        }
        return released;
    }
}
