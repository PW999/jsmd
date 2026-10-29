# Tasks

## 1. Queue monitor service

- [x] 1.1 Add `QueueMonitor` (package `queue`) with `QueueCounts`/`QueueItemRow` records, `counts()` and `items(limit)` running in a read-only `TransactionTemplate` at `ISOLATION_READ_UNCOMMITTED` without row locks; ordering failed → claimed → pending → finished with a representative file path. Verify with `QueueMonitorIT` (Testcontainers, like `WorkQueueIT`): counts per queue/status, ordering, limit, and path shown/absent.
- [x] 1.2 Add `removeFailed(id)` and `purgeFailed()` as `DELETE ... WHERE status = 'FAILED'`. Verify in `QueueMonitorIT`: only failed rows are deleted, pending/claimed/done untouched, purge returns the removed count, remove of a non-failed or missing id returns false, and the content can be enqueued again after removal.
- [x] 1.3 Verify non-blocking: in `QueueMonitorIT`, hold an open transaction with an uncommitted update (and a `FOR UPDATE` lock) on a `work_item` row and assert `counts()`/`items()` return promptly (with a timeout) and that a concurrent `claim` is not delayed by the monitor; assert the isolation level requested by the monitor's transaction.
- [x] 1.4 Document in `README.md` the Queue page and the PostgreSQL READ UNCOMMITTED behaviour; verify the README renders the new section.

## 2. Queue page

- [x] 2.1 Add `QueueController` with `GET /queue`, `POST /queue/items/{id}/remove` and `POST /queue/purge` (redirect with flash messages for removed / not failed / not found / purged N). Verify with `QueueControllerTest` (`@WebMvcTest`, mocked `QueueMonitor`): view and model, redirects, messages, and that remove/purge call the monitor.
- [x] 2.2 Add `templates/queue.html` in the standard layout: status counts per queue, item table (queue, status, attempts, last error, created, path), empty-state and truncation notice, per-row Remove form only for failed items, Purge form with confirmation shown only when failures exist, and a script that refreshes the live region every 5 seconds without reloading the page. Add the needed rules to `static/css/app.css`. Verify in `QueueControllerTest`: live-region refresh hook present (no meta refresh), Remove/Purge controls shown only when failed items exist, text with markup is escaped.
- [x] 2.3 Add the Queue entry (`active == 'queue'`) to the menu in `layout.html`. Verify in `LayoutTest` that the entry exists and is marked active on `/queue`.
- [x] 2.4 Verify access control in `QueueControllerTest`/`SecurityConfigTest`: anonymous requests to the page and both POSTs redirect to login, and POSTs without a CSRF token are rejected with 403 and change nothing.

## 3. Integration

- [x] 3.1 Extend `ApplicationFlowIT` (or add a case) to log in, open `/queue`, remove a failed item and purge, asserting the database afterwards. Run `mvn verify` and confirm all tests pass.
- [x] 3.2 Run the app with the dev profile (`run` skill), open `/queue` while the extraction scheduler is working, and confirm the page refreshes and Remove/Purge behave as specified.
