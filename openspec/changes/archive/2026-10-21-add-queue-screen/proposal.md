# Proposal

## Why

The process and delete queues are only visible through the database. The administrator cannot see what is waiting, what is being worked on, or which items failed permanently (attempts exhausted), and has no way to clear failed items short of running SQL.

## What Changes

- Add a **Queue** page to the left menu showing the contents of the work queues: per queue and status counts, plus a list of items with their queue, status, attempts, last error and age.
- The page refreshes itself periodically so progress can be watched without reloading.
- Failed items can be removed one by one, and a **Purge** button removes all failed items at once. Removing never touches pending or claimed items.
- Every read of the queue table made for this screen runs at the READ UNCOMMITTED isolation level, so that monitoring never interferes with the claim/complete/fail logic of the queues.
- Removal and purge are POST actions protected by CSRF, available to the administrator only.

## Capabilities

### New Capabilities
- `queue-monitor`: The Queue page — listing, status counts, auto-refresh, removal of failed items and purge.

### Modified Capabilities
- `work-queues`: Adds the ability to remove failed items and to purge all failed items, and requires that monitoring reads do not take locks that interfere with queue operation.
- `app-shell`: The left menu gains a Queue entry.

## Impact

- New code in `queue` (read/remove operations next to `WorkQueue`) and `web` (a controller), plus a `queue.html` template and a few CSS rules.
- `layout.html` menu gets a new entry.
- No schema change and no new dependency.
- Tests: repository integration tests (Testcontainers, like `WorkQueueIT`), a controller test and a layout/menu test.
- Out of scope: retrying failed items, editing items, filtering/pagination beyond a capped list, and changing the claim/complete/fail logic itself.
