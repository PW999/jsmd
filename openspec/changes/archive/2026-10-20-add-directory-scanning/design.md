# Design

## Context

jsmd is a single-module Spring Boot 4.1 / Java 27 app using Spring JDBC (no JPA), Flyway (only `V1__init.sql`, an empty baseline) and PostgreSQL 17. Configuration is `application.yaml` with `${ENV:default}` placeholders; integration tests (`*IT`) use Testcontainers. There is no domain model yet. See proposal.md for motivation and the specs (`directory-scanning`, `work-queues`) for required behavior.

## Goals / Non-Goals

**Goals:**
- Correct classification of new / changed / moved / duplicate / removed files, including when several happen in one scan.
- Content stored once; each content processed at most once.
- Safe on large trees and when a directory is temporarily unavailable.

**Non-Goals:**
- Processing queue items or deleting content (later changes; this one only provides the queue API).
- Real-time filesystem watching, UI, scanning remote/object storage.

## Decisions

**Data model (Flyway `V2__file_index_and_queues.sql`), plain JDBC via `JdbcClient`.**
- `content(id, sha256 bytea UNIQUE, size, created_at)`: one row per distinct hash. This is where later changes attach the extracted text.
- `file_location(id, path text UNIQUE, content_id → content, size, modified_at, last_seen_scan_id)`: every known path. Many locations may share one content.
- `work_item(id, queue text CHECK in ('PROCESS','DELETE'), content_id → content, status ('PENDING','CLAIMED','DONE','FAILED','CANCELLED'), attempts, available_at, claimed_at, last_error, created_at)` with a partial unique index on `(queue, content_id) WHERE status IN ('PENDING','CLAIMED')`. That index gives "no duplicate pending items" and lets enqueue be `INSERT ... ON CONFLICT DO NOTHING`.
Alternative: one table with the hash on `file_location`. Rejected: it makes "store content once" and later text storage awkward and cannot express copies cleanly.

**Hash: SHA-256, streamed.** The user suggested MD5/SHA-1; both are collision-prone and identity here drives deduplication, so a collision would silently merge two different documents. SHA-256 is in the JDK and its cost is dominated by IO. Files are streamed with a fixed buffer (never loaded whole). Paired with `size` for a cheap sanity check.

**Skip hashing when `(path, size, mtime)` is unchanged.** Compare with the stored location; only changed or unknown paths are hashed. Trade-off: a content change that preserves size and mtime is missed; acceptable for this use case and standard (rsync-style). mtime is compared at millisecond precision to avoid filesystem rounding differences.

**Two-phase scan: walk, then reconcile.** Moves and removals are only knowable after seeing the whole tree, so a scan:
1. Walks each root with `Files.walkFileTree` (no symlink following), tagging each seen path with the scan id. Unchanged paths just get `last_seen_scan_id` updated. Changed or unknown paths are hashed and collected as candidates. Read errors keep the old state and mark the path as seen.
2. Reconciles in one transaction per scan (or per batch of candidates):
   - Candidate path known with different hash → changed: repoint the location; if the new hash is unknown, create content and enqueue PROCESS; if the old content now has no locations, enqueue DELETE.
   - Candidate path unknown: hash exists on a location of this root set **not seen in this scan** → move: update that location's path (consumes the missing location so one missing file explains one new file); hash exists on a still-present location → duplicate, insert another location; hash unknown → new content + PROCESS.
   - Locations under an available root not seen in this scan and not consumed by a move → removed: delete the location; if its content has no locations left, enqueue DELETE.
   - A candidate whose hash matches content with a pending DELETE item cancels that item (status `CANCELLED`) and reuses the content with no PROCESS item.
Candidates are processed after all hashing so move detection does not depend on directory walk order.

**Removal safety.** Removal is only evaluated for roots that were fully walked. If a root is missing, unreadable, or the walk of it fails, none of its locations are touched. This prevents a temporarily unmounted share from queueing everything for deletion.

**Scheduling and exclusivity.** `@EnableScheduling` and `@Scheduled(fixedDelayString = "${jsmd.scan.interval}")` (fixed delay, so runs never overlap within one instance, and the next starts after the previous ends). Across instances, a scan takes a PostgreSQL transaction-scoped advisory lock (`pg_try_advisory_xact_lock`) held on a dedicated connection for the duration of the scan and skips if it cannot get it (transaction scope means the database releases it even if the process dies or the pooled connection is recycled). Alternative ShedLock: another dependency for one lock. Alternative: relying on a single instance: not enforceable.

**Configuration.** `@ConfigurationProperties("jsmd.scan")` record: `enabled` (default true), `directories` (list of paths; env `JSMD_SCAN_DIRECTORIES`, comma-separated), `interval` (`Duration`, default `PT5M`, env `JSMD_SCAN_INTERVAL`). Validation at startup: paths normalised to absolute, and a root nested inside another fails startup (it would double-index and confuse move/removal logic). A missing directory is not a startup error since mounts can come and go; it is logged per scan. Empty list means no scans. No default directory.

**Queue API.** A `WorkQueue` service on JDBC: `enqueue(queue, contentId)`, `claim(queue)` (single statement using `FOR UPDATE SKIP LOCKED`, ordered by `available_at, id`, sets `CLAIMED`, `claimed_at`, increments `attempts`), `complete(id)`, `fail(id, error)` (back to `PENDING` with `available_at = now + retryDelay` or `FAILED` after `maxAttempts`), `cancelPending(queue, contentId)` (only `PENDING`), and a requeue of stale claims (`claimed_at` older than a timeout) run by a small scheduled job or at claim time. Retry delay, max attempts and claim timeout come from `jsmd.queue.*` with defaults. Delivery is at-least-once, so consumers must be idempotent. Alternatives: in-memory (loses work on restart), external broker (new infrastructure; not warranted). Using the DB keeps enqueue transactional with the index change, so a content can never be recorded without its queue item.

**Delete queue is intent only.** The scanner never deletes `content`. A later delete consumer must check, in its own transaction, that the content still has no locations before deleting. This covers the race where a file reappears after the delete item was claimed (cancel has no effect on claimed items).

**Package layout.** `com.pw999.jsmd.scan` (properties, scanner, hashing, reconciliation, scheduler) and `com.pw999.jsmd.queue` (WorkQueue, queue enum). No changes to web or security code.

**Testing.** Unit tests for hashing and the classification logic using a temp directory; `*IT` tests on Testcontainers Postgres for reconciliation scenarios (new, change, move, copy, removal, move + new in same scan, root unavailable, reappearing content) and queue behavior (concurrent claim, retry, stale claim, duplicate suppression).

## Risks / Trade-offs

- [Unmounted or unreadable root looks like mass deletion] → removals evaluated only for fully walked roots; scan logs a warning for skipped roots.
- [File being written during a scan is hashed half-written] → treat a file whose mtime is within a short settle window (default a few seconds) of scan start as not ready and pick it up next scan; the changed-file path then handles it anyway.
- [Large trees: memory and runtime] → stream the walk, hash only changed/unknown files, process candidates in batches; the whole scan holds only candidate records, not the tree.
- [Move detection by hash is ambiguous with identical copies] → a missing location is matched to one new path; remaining new paths with the same hash become duplicates. Which path "moved" is arbitrary but the resulting index is identical.
- [Size+mtime shortcut misses same-size, same-mtime edits] → accepted; documented.
- [At-least-once delivery] → consumers must be idempotent.
- [A delete item already claimed cannot be cancelled] → delete consumer re-checks locations before deleting.

## Migration Plan

Deploy with `V2` applied automatically by Flyway at startup. With no directories configured nothing runs, so deployment is inert until configured. Rollback: stop the application, drop the new tables (`work_item`, `file_location`, `content`) and remove the `V2` row from `flyway_schema_history`, or restore from backup; the V1 baseline is unaffected.
