# directory-scanning Specification

## Purpose

Discovers documents by periodically scanning configured directories and classifying every file as new, changed, moved, duplicate, unchanged or removed, so each distinct content is stored and processed only once.

## Requirements

### Requirement: Scan configuration
The system SHALL read the directories to scan, the scan interval and an enabled flag from configuration, with environment variables taking precedence. When no directories are configured, the system SHALL start normally and perform no scans.

#### Scenario: Directories configured
- **WHEN** one or more directories are configured and scanning is enabled
- **THEN** each directory is scanned recursively on every scan interval

#### Scenario: No directories configured
- **WHEN** no directories are configured
- **THEN** the application starts and no scan is performed

#### Scenario: Overlapping directories
- **WHEN** one configured directory is inside another configured directory
- **THEN** startup fails with an error naming both directories

#### Scenario: Unavailable directory
- **WHEN** a configured directory does not exist or cannot be read during a scan
- **THEN** the problem is logged, the other directories are still scanned, and no file under the unavailable directory is treated as removed

### Requirement: Scheduled and exclusive scanning
The system SHALL scan on a fixed schedule and SHALL NOT run two scans at the same time, including across several running instances sharing one database.

#### Scenario: Scan still running at next tick
- **WHEN** the scan interval elapses while the previous scan is still running
- **THEN** the new scan is skipped

#### Scenario: Two instances
- **WHEN** two application instances use the same database
- **THEN** at most one of them scans at any moment

### Requirement: Content hash identity
The system SHALL identify file content by a SHA-256 hash of the file's bytes and SHALL store each distinct hash once, regardless of how many files have that content. The system SHALL avoid re-hashing a file whose path, size and modification time are unchanged since the last scan.

#### Scenario: Unchanged file
- **WHEN** a known file has the same size and modification time as at the last scan
- **THEN** it is not re-read, not re-hashed and nothing is enqueued

#### Scenario: Touched but identical
- **WHEN** a known file's modification time changed but its hash is the same
- **THEN** nothing is enqueued and the stored modification time is updated

### Requirement: Detect new files
The system SHALL treat a file whose path is unknown and whose hash is unknown as new content and enqueue it on the process queue.

#### Scenario: New file
- **WHEN** a file appears whose path and content hash are both unknown
- **THEN** the content is recorded and one item is enqueued on the process queue

### Requirement: Detect changed files
The system SHALL treat a known path whose hash differs from the recorded one as changed. The new content SHALL be enqueued on the process queue if its hash is unknown, and the old content SHALL be enqueued on the delete queue if no other file still has it.

#### Scenario: File modified
- **WHEN** a known file's content changes to a hash not seen before
- **THEN** the new content is enqueued on the process queue and the path now refers to the new content

#### Scenario: Old content no longer used
- **WHEN** a file changes and no other known file has the old content
- **THEN** the old content is enqueued on the delete queue

#### Scenario: Old content still used elsewhere
- **WHEN** a file changes and another known file still has the old content
- **THEN** the old content is not enqueued on the delete queue

### Requirement: Detect moved files
The system SHALL treat a file as moved when its hash equals that of a known file whose path no longer exists on disk. A moved file SHALL update the recorded path and SHALL NOT be enqueued on any queue.

#### Scenario: File moved within a directory tree
- **WHEN** a file is moved to another location in a scanned directory
- **THEN** its recorded path is updated, nothing is enqueued, and the content is not duplicated

#### Scenario: File moved between scanned directories
- **WHEN** a file is moved from one configured directory to another
- **THEN** it is treated as a move in the same way

### Requirement: Prevent duplicate content
The system SHALL NOT store or process the same content twice. A file whose hash is already known and whose original file still exists SHALL be recorded as an additional location of the existing content and SHALL NOT be enqueued.

#### Scenario: Copy of an existing file
- **WHEN** a new path has the same hash as a known file that still exists
- **THEN** the new path is recorded as another location of the same content and nothing is enqueued

#### Scenario: Copies deleted one by one
- **WHEN** content has several locations and all but one are removed
- **THEN** the content stays and nothing is enqueued on the delete queue

### Requirement: Detect removed files
The system SHALL treat a known path that is no longer on disk, and that is not explained by a move, as removed. When the removed path was the last location of its content, the content SHALL be enqueued on the delete queue.

#### Scenario: Last location removed
- **WHEN** the only file with some content is deleted from disk
- **THEN** the content is enqueued on the delete queue

#### Scenario: Removal only detected after full scan
- **WHEN** a file disappears and an identical file appears elsewhere in the same scan
- **THEN** it is treated as a move, not as a removal plus a new file

#### Scenario: Previously removed content reappears
- **WHEN** a file appears with the hash of content that is queued for deletion
- **THEN** the pending delete item is cancelled and the content is reused without being enqueued on the process queue

### Requirement: Scan resilience
The system SHALL continue a scan when a single file cannot be read, and SHALL NOT treat a file it failed to read as removed or changed.

#### Scenario: Unreadable file
- **WHEN** a file cannot be read during a scan
- **THEN** the error is logged with the path, the file's previous state is kept, and other files are processed

#### Scenario: Symbolic links
- **WHEN** a scanned directory contains symbolic links
- **THEN** they are not followed and not indexed
