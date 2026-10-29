# Tasks

## 1. Purge finished items

- [x] 1.1 Replace `QueueMonitor.purgeFailed()` with `purgeFinished()` deleting FAILED, DONE and CANCELLED items; update `QueueMonitorIT` so pending and claimed items survive and done/cancelled/failed are removed, and verify with `mvn -Dtest=QueueMonitorIT verify`
- [x] 1.2 Update `QueueController.purge` (flash message "Purged N finished item(s).") and `queue.html`: render the Purge button always (also when queues are empty), with the new confirm text; update `QueueControllerTest` and `ApplicationFlowIT` and verify the button is present for empty queues and for queues without failed items
- [x] 1.3 Update README queue/Queue-page description if it mentions purging failed items, and verify the text matches the new behaviour

## 2. Daily retention cleanup

- [x] 2.1 Add `retention` and `cleanupCron` to `QueueProperties` and `application.yaml` (`JSMD_QUEUE_RETENTION`, `JSMD_QUEUE_CLEANUP_CRON`), document both in the README configuration table, and verify the application context starts with defaults and rejects a non-positive retention
- [x] 2.2 Add `QueueMonitor.deleteOlderThan(Instant)` and an IT proving old items of every status are deleted and recent ones kept
- [x] 2.3 Add `QueueRetentionScheduler` (daily cron, injected `Clock`, logs removed count, catches and logs exceptions) with a unit test for the cutoff calculation and error handling, and verify with `mvn test`
- [x] 2.4 Confirm a re-scan does not misbehave when DONE items have been removed (check `DirectoryScanner` queueing) and cover it with a test

## 3. Integration

- [x] 3.1 Run `mvn verify` and `openspec validate purge-all-finished-and-expire-queue-items --strict`; both pass
