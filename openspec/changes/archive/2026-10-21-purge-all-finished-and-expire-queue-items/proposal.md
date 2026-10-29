# Proposal

## Why

The Purge button on the Queue page only removes failed items and disappears when none are failed, so finished (done, cancelled) items pile up with no way to clear them. Nothing ever removes old items either, so the `work_item` table and the Queue page grow without bound.

## What Changes

- The Purge button is always visible on the Queue page, also when the queues are empty or hold no failed items.
- Purge removes all finished items: failed, done and cancelled. Pending and claimed items are never purged, so queued work is not lost and running consumers are not disturbed.
- The confirmation text and result message reflect the broader scope.
- A daily scheduled job removes every queue item older than one week, whatever its status (age is measured from the item's creation time).
- The retention period and the daily run time are configurable, with defaults of 7 days and once a day.

## Capabilities

### New Capabilities
- `queue-retention`: automatic removal of queue items that exceed the retention period, on a daily schedule.

### Modified Capabilities
- `queue-monitor`: the "Purge failed items" requirement becomes "Purge finished items" and its button is always offered.

## Impact

- `QueueMonitor`: `purgeFailed()` replaced by a purge of failed, done and cancelled items; new age-based delete.
- `QueueController` and `queue.html`: button always rendered, new wording and flash message.
- New scheduled component and `jsmd.queue` properties (`retention`, cleanup cron); `application.yaml` and README configuration table.
- Tests: `QueueMonitorIT`, `QueueControllerTest`, new retention scheduler test, `ApplicationFlowIT` if it uses purge.
- No schema change; no new dependencies.
