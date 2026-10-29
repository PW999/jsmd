# Tasks

## 1. Schema and queues

- [x] 1.1 Add Flyway `V2__file_index_and_queues.sql` creating `content`, `file_location` and `work_item` (with the partial unique index on pending/claimed items); verify via a Testcontainers IT that the migration applies on an empty database and the unique index rejects a second pending item for the same queue and content
- [x] 1.2 Add `jsmd.queue.*` properties (retry delay, max attempts, claim timeout) with defaults in `application.yaml`; verify a unit test binds defaults and overrides
- [x] 1.3 Implement the `WorkQueue` service (enqueue with `ON CONFLICT DO NOTHING`, claim with `SKIP LOCKED`, complete, fail with retry/permanent failure, cancel pending, requeue stale claims); verify an IT covering duplicate suppression, concurrent claim (exactly one winner), retry delay, attempts exhausted, stale claim re-offered, and cancel before/after claim
- [x] 1.4 Document the queue tables and retry settings in the README; verify the documented settings match `application.yaml`

## 2. Scan configuration and scheduling

- [x] 2.1 Add `jsmd.scan.*` configuration properties (`enabled`, `directories`, `interval`) with startup validation (absolute normalised paths, reject nested roots); verify unit tests for empty list, comma-separated env value, nested roots failing with both paths named
- [x] 2.2 Add `@EnableScheduling`, the scheduled scan entry point with `fixedDelay`, and the PostgreSQL advisory-lock guard; verify an IT showing a second concurrent attempt skips while the lock is held and a no-directories config performs no scan
- [x] 2.3 Add `JSMD_SCAN_DIRECTORIES` and `JSMD_SCAN_INTERVAL` to `.env.example` and the README configuration table; verify the app still starts with only the admin credentials set (existing `ApplicationFlowIT` passes)

## 3. Hashing and walking

- [x] 3.1 Implement streamed SHA-256 hashing; verify a unit test against known digests for empty, small and multi-buffer files
- [x] 3.2 Implement the directory walker (recursive, symlinks not followed, per-file read errors logged and the file kept as seen, per-root availability reported, recently modified files skipped as not settled); verify unit tests with temp directories for symlinks, unreadable file, missing root and unsettled file

## 4. Reconciliation

- [x] 4.1 Implement the index repository (lookup by path, hash, locations not seen in scan) and the unchanged-file shortcut on size and mtime; verify an IT that an unchanged tree causes no hashing, no queue items and only `last_seen` updates
- [x] 4.2 Implement new, changed and touched-but-identical handling, including PROCESS enqueue and DELETE enqueue of orphaned old content; verify ITs for each spec scenario in "Detect new files" and "Detect changed files"
- [x] 4.3 Implement move detection (hash matches a location missing from the scan, in the same or another root) and duplicate handling (hash matches a still-present location); verify ITs for move within a tree, move between roots, copy of an existing file, move plus new file in the same scan, and copies removed one by one
- [x] 4.4 Implement removal handling (last location removed enqueues DELETE; roots not fully walked are never evaluated) and reappearing content (cancel the pending DELETE, no PROCESS); verify ITs for last-location removal, unavailable root with files untouched, and a file reappearing before the delete item is claimed
- [x] 4.5 Wire the walker and reconciliation into the scheduled scan with per-scan transaction handling and summary logging (counts of new, changed, moved, duplicate, removed); verify an end-to-end IT that runs a scan over a temp directory through create, modify, move, copy and delete steps and asserts the index and both queues after each step
- [x] 4.6 Add a short README section describing scanning behavior (how new/changed/moved/duplicate/removed are determined, SHA-256, the size+mtime shortcut limitation, delete queue being intent-only); verify the section matches the specs

## 5. Integration check

- [x] 5.1 Run `mvn verify` with Docker running and confirm all unit and integration tests pass, including the pre-existing ones
