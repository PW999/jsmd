# Design

## Context

Queues live in one table, `work_item` (statuses `PENDING`, `CLAIMED`, `DONE`, `FAILED`, `CANCELLED`), operated by `WorkQueue` with single-statement operations (`FOR UPDATE SKIP LOCKED` for claiming). The database is PostgreSQL, accessed through `JdbcClient`; the UI is server-rendered Thymeleaf behind form login with CSRF enabled (Spring Security defaults). See proposal.md for motivation.

Important constraint: **PostgreSQL does not implement READ UNCOMMITTED.** Requesting it gives READ COMMITTED behaviour (no dirty reads). Under MVCC, plain `SELECT`s never block on, or block, writers, so the actual goal — monitoring must not break the queueing logic — is already met as long as the screen never takes row locks (`FOR UPDATE`, `FOR SHARE`). The requirement is still honoured literally by requesting the READ UNCOMMITTED level, which is also correct should the database ever change.

## Goals / Non-Goals

**Goals:**
- A read-only monitoring path that is explicitly read-uncommitted, lock-free and isolated from `WorkQueue`.
- Remove-one and purge for failed items that cannot touch live (pending/claimed) items.

**Non-Goals:**
- Retrying/requeueing failed items, per-queue filters, pagination, or live push (SSE/WebSocket).
- Changing `WorkQueue` claim/complete/fail semantics or the schema.

## Decisions

**1. Separate `QueueMonitor` service in `queue`, not new methods on `WorkQueue`.** `WorkQueue` is documented as single-statement operations that join a caller's transaction; monitoring reads need their own isolation level and have a different caller (the web layer). `QueueMonitor` offers `counts()`, `items(limit)`, `removeFailed(id)` and `purgeFailed()`. Alternative (extend `WorkQueue`) rejected: it would mix consumer-facing and admin-facing concerns and make the isolation level of a shared class ambiguous.

**2. Read-uncommitted via a `TransactionTemplate` with `ISOLATION_READ_UNCOMMITTED` and `readOnly`.** Reads (`counts`, `items`) run inside one such transaction so that the counts and list are one consistent snapshot of one statement set. Spring sets the level on the connection and resets it afterwards, so pooled connections used by `WorkQueue` are unaffected. Alternative: `SET TRANSACTION ISOLATION LEVEL` by hand — rejected, `TransactionTemplate` is the idiom and resets cleanly. Alternative: `@Transactional(isolation = ...)` — equivalent, but a template keeps the behaviour visible in code and testable without proxies in a plain `JdbcClient` integration test. Reads never use `FOR UPDATE`/`FOR SHARE`.

**3. Removal is `DELETE ... WHERE status = 'FAILED'`.** Both remove-one (`AND id = ?`) and purge are single statements, atomic and safe against races: an item that was concurrently retried or claimed (not possible for `FAILED` today, but guarded anyway) is simply not matched. Remove-one returns whether a row was deleted; the controller distinguishes "gone" from "not failed" with a follow-up uncommitted lookup only to pick the message. These writes are normal READ COMMITTED statements — only reads are uncommitted. Hard delete (not a new status) because the status check constraint has no "removed" value and a schema change is not warranted; failed items carry no state needed elsewhere. `work_item_open_uq` only covers pending/claimed, so removal never conflicts with re-enqueue.

**4. Item list query.** One query ordered by status rank (`FAILED`, `CLAIMED`, `PENDING`, then `DONE`/`CANCELLED`), then `created_at DESC, id DESC`, `LIMIT 200`, with a `LEFT JOIN LATERAL (SELECT min(path) FROM file_location WHERE content_id = wi.content_id)` to show a representative path (a content can have several paths; contents that lost all paths show none, as for delete items). Status counts come from a separate `GROUP BY queue, status`, so counts stay correct when the list is truncated. `DONE` items are not purged by anything today and may be numerous; the cap plus ordering keeps the page cheap, and the truncation notice tells the user.

**5. Auto-refresh by fetching the page and swapping the live region.** A small inline script re-requests `/queue` every 5 seconds (`data-refresh-seconds` on `<main>`), parses the HTML and replaces only the content of `#queue-live` (counts, purge form, items). No reload, so scroll position and the flash message survive; no new endpoint, because the normal page is the data source. It skips rounds while the tab is hidden, ignores failed requests and reloads the page when the response was a redirect (session expired, so the login page shows). Forms inside the region are replaced together with their CSRF tokens. Alternatives: `<meta http-equiv="refresh">` rejected (reloads the whole page, resets scroll); SSE/WebSocket out of scope.

**6. Web layer.** `QueueController` (package `web`, package-private like `DocumentsController`): `GET /queue`, `POST /queue/items/{id}/remove`, `POST /queue/purge`. POSTs redirect (PRG) to `/queue` with a flash message (`removed`, `not-failed`, `not-found`, `purged N`). Forms use Thymeleaf `th:action`, which adds the CSRF token. Purge requires confirmation per spec, done with `onsubmit="return confirm(...)"` on the button's form — a small inline handler; the server remains authoritative since it only deletes `FAILED` rows. Menu entry gets `active == 'queue'`. The Purge button is rendered only when the failed count is greater than zero.

**7. Failed items are only the `FAILED` status.** Items waiting for a retry (`PENDING` with `last_error`) are shown with their last error but cannot be removed; removing them would drop work that will still be attempted.

## Risks / Trade-offs

- [READ UNCOMMITTED is a no-op on PostgreSQL, so a reader expecting dirty reads gets committed data] → Documented here and in code comment; no behavioural requirement depends on dirty reads, only on not blocking, which MVCC guarantees.
- [Region swap replaces a Remove button just as the user clicks it] → The click posts the form it hit; the server only deletes `FAILED` rows, so the worst case is a "not failed / not found" message.
- [Purge deletes data irrecoverably] → Confirmation prompt; only `FAILED` rows; the underlying files and content remain and the next scan does not re-enqueue unchanged content, so purged items stay unprocessed until the content changes. This is the intended meaning of "remove".
- [Uncommitted read of `file_location` while a scan is mid-run shows transient paths] → Cosmetic only.
