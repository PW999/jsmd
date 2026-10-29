# Spec Delta

## ADDED Requirements

### Requirement: Removing failed items
The system SHALL allow a permanently failed item to be deleted individually, and all permanently failed items to be deleted at once. Items that are pending, claimed, done or cancelled SHALL NOT be deleted by these operations.

#### Scenario: Remove one failed item
- **WHEN** a failed item is removed
- **THEN** it no longer exists and cannot be claimed

#### Scenario: Remove an item that is not failed
- **WHEN** removal is requested for a pending or claimed item
- **THEN** the item is unchanged

#### Scenario: Purge failed items
- **WHEN** all failed items are purged while pending and claimed items exist
- **THEN** every failed item is deleted, the number removed is reported, and the other items are unchanged

#### Scenario: Content can be queued again after removal
- **WHEN** a failed item has been removed and its content is enqueued on the same queue
- **THEN** a new pending item is created

### Requirement: Non-blocking inspection
The system SHALL provide read access to queue items and status counts that runs read-uncommitted and takes no row locks, so that inspecting the queues never delays claiming, completing or failing items.

#### Scenario: Inspect during open transaction
- **WHEN** another transaction has modified a queue item and not yet committed, and the queues are inspected
- **THEN** the inspection returns without waiting for that transaction
