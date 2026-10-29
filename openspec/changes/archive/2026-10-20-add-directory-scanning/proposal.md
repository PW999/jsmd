# Proposal

## Why

The application is meant to analyze documents and store searchable text, but it has no way to discover documents. Files live in directories that other systems write to, so jsmd must find them itself: periodically scan configured directories, work out which files are new, changed, moved or removed, and hand the work to a processing step later. Content must be stored only once, so identical files are never processed twice.

## What Changes

- Add a scheduled scanner that walks one or more configured directories (recursively) and compares what it finds with a persistent index of known files.
- Identify files by a SHA-256 content hash, so the scanner can classify each file as new, changed, moved, duplicate, unchanged or removed.
- Keep the content of each distinct hash in the database exactly once; several paths may point at the same content.
- Add two PostgreSQL-backed queues:
  - a **process** queue: a work item is enqueued when new content (a hash not yet known) is found;
  - a **delete** queue: a work item is enqueued when content no longer has any file on disk.
- Add the queue API (enqueue, claim, complete, fail with retry) used by later consumers. Consumers themselves are not part of this change.
- Add configuration for the directories, scan interval and enabling the scanner, with environment-variable overrides, and document it in the README.
- Add Flyway migration(s) for the file index and queue tables.

Out of scope: extracting text or analyzing documents, the consumers of either queue, deleting content from the database (the delete queue only records the intent), UI for managing directories or viewing the queues, and watching directories in real time.

## Capabilities

### New Capabilities
- `directory-scanning`: scheduled scanning of configured directories, hash-based classification of files (new, changed, moved, duplicate, unchanged, removed), the file index, and the configuration of directories and schedule.
- `work-queues`: the PostgreSQL-backed process and delete queues, their lifecycle (pending, claimed, done, failed with retry) and the guarantee that identical content is enqueued only once.

### Modified Capabilities
<!-- None: existing specs (app-configuration, admin-authentication, app-shell) keep their requirements. -->

## Impact

- **Code**: new package(s) under `com.pw999.jsmd` for scanning and queues; `@EnableScheduling` on the application.
- **Database**: new Flyway migration `V2__...` creating the file index and queue tables.
- **Configuration**: new `jsmd.scan.*` settings (`JSMD_SCAN_DIRECTORIES`, `JSMD_SCAN_INTERVAL`, ...) in `application.yaml`, `.env.example` and README. With no directories configured the scanner does nothing, so existing deployments are unaffected.
- **Dependencies**: none new (JDK `java.nio.file` and `MessageDigest`; Spring JDBC already present).
- **Operations**: the application needs read access to the scanned directories; scans touch the database in bulk, so large trees are scanned incrementally using size and modified-time to avoid re-hashing.
