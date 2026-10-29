# Spec Delta

## Purpose

Keeps the work queues from growing without bound by automatically removing queue items once they are older than the retention period.

## ADDED Requirements

### Requirement: Expire old queue items
The system SHALL remove every queue item whose creation time is more than the retention period ago, whatever its status. The retention period SHALL default to one week and SHALL be configurable.

#### Scenario: Old items removed
- **WHEN** the cleanup runs and items of any status were created more than a week ago
- **THEN** those items are deleted

#### Scenario: Recent items kept
- **WHEN** the cleanup runs and items were created less than a week ago
- **THEN** those items are left untouched, whatever their status

### Requirement: Daily cleanup schedule
The system SHALL run the cleanup automatically once a day. The time of day SHALL be configurable and cleanup failures SHALL be logged without stopping later runs.

#### Scenario: Daily run
- **WHEN** the configured time of day is reached
- **THEN** the cleanup runs and logs how many items it removed

#### Scenario: Cleanup fails
- **WHEN** the cleanup throws an error, for example because the database is unreachable
- **THEN** the error is logged and the cleanup runs again at the next scheduled time
