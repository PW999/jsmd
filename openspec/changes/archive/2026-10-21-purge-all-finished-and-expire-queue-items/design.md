# Design

## Context

`QueueMonitor` already owns the admin-side deletes (`removeFailed`, `purgeFailed`) over the `work_item` table, and `QueueController` renders the purge form only when `total > 0` and `failedCount > 0`. Scheduled jobs (`ScanScheduler`, `ExtractionScheduler`) are `@Component`s using `@Scheduled` with `@EnableScheduling` on the application class. `work_item` has `created_at` but no updated/finished timestamp. `QueueProperties` holds `retryDelay`, `maxAttempts`, `claimTimeout`.

## Goals / Non-Goals

**Goals:**
- Purge button always rendered; purge covers FAILED, DONE and CANCELLED.
- Daily deletion of items older than the retention period, any status.

**Non-Goals:**
- Purging pending or claimed items by hand.
- Adding a finished-at column or migration.
- Cluster-wide coordination of the cleanup.

## Decisions

- **Purge scope: finished only.** A single `DELETE ... WHERE status IN ('FAILED','DONE','CANCELLED')`. Deleting pending items would drop queued work; deleting claimed items would make a consumer's `complete`/`fail` hit a missing row. Renamed `purgeFailed` to `purgeFinished`; the endpoint `/queue/purge` is unchanged.
- **Button outside the conditional blocks.** Moved above the `total == 0` check in `queue.html` so it renders even for empty queues; confirm text becomes "Remove all finished items (failed, done, cancelled) from all queues?". The message pluralises as before ("Purged N finished item(s).").
- **Retention measured from `created_at`, all statuses.** Requested explicitly. Consequence: an item still pending/claimed after a week is also deleted. Alternative (a new `finished_at`) rejected as it needs a migration and was not asked for.
- **Cleanup in a new `QueueRetentionScheduler`** in the `queue` package, calling `QueueMonitor.deleteOlderThan(Instant)` (`DELETE FROM work_item WHERE created_at < ?`). Scheduled with `@Scheduled(cron = "${jsmd.queue.cleanup-cron}")`, default `0 30 3 * * *` (03:30 daily, server timezone). Exceptions are caught and logged like `ScanScheduler`. Keeping the delete in `QueueMonitor` reuses the existing `JdbcClient` code instead of a second data-access class; the SQL is a single idempotent statement, so concurrent runs on several instances are harmless and need no lock.
- **Configuration.** `QueueProperties` gains `Duration retention` (`${JSMD_QUEUE_RETENTION:P7D}`, validated positive) and `String cleanupCron` (`${JSMD_QUEUE_CLEANUP_CRON:0 30 3 * * *}`). Both are added to the README config table. A `Clock` is injected so tests control "now".

## Risks / Trade-offs

- [Stuck pending/claimed item deleted after a week] → intended by the requester; the cleanup logs the number removed so it is visible.
- [Re-scan re-queues content whose DONE item was purged] → the scanner decides queueing from file state, not from old items; to be confirmed against `DirectoryScanner` during implementation, with a test.
- [Big first cleanup on a large table] → single indexed-free delete of old rows is acceptable at this scale; revisit with batching only if it proves slow.
