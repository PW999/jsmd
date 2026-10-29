# Spec Delta

## Purpose

Provides durable process and delete queues in the database that hold work for later consumers, with at-least-once delivery, retries, and at most one pending item per content per queue.

## ADDED Requirements

### Requirement: Durable queues
The system SHALL provide a process queue and a delete queue whose items refer to a stored content and survive application restarts.

#### Scenario: Restart with pending items
- **WHEN** the application restarts while items are pending
- **THEN** the items are still pending and can be claimed

### Requirement: No duplicate pending items
The system SHALL hold at most one pending or in-progress item per content per queue.

#### Scenario: Enqueued twice
- **WHEN** an item is enqueued for content that already has a pending item on the same queue
- **THEN** no second item is created

#### Scenario: Same content on both queues
- **WHEN** content has a pending item on the process queue and is then enqueued on the delete queue
- **THEN** both items exist independently

### Requirement: Claiming items
The system SHALL let a consumer claim the oldest available item of a queue so that no two consumers receive the same item at once.

#### Scenario: Concurrent consumers
- **WHEN** two consumers claim from a queue holding one item at the same time
- **THEN** exactly one of them receives it

#### Scenario: Empty queue
- **WHEN** a consumer claims from a queue with no available items
- **THEN** it receives nothing

### Requirement: Completion, failure and retry
The system SHALL let a consumer mark a claimed item done or failed. A failed item SHALL become available again after a delay until a maximum number of attempts is reached, after which it SHALL be marked failed permanently. A claim not completed within a timeout SHALL become available again.

#### Scenario: Item completed
- **WHEN** a consumer marks a claimed item done
- **THEN** it is no longer available to any consumer

#### Scenario: Item fails and retries
- **WHEN** a consumer marks a claimed item failed and attempts remain
- **THEN** the item becomes available again after the retry delay

#### Scenario: Attempts exhausted
- **WHEN** an item fails on its last allowed attempt
- **THEN** it is marked permanently failed and is not offered again

#### Scenario: Consumer crashes
- **WHEN** a claimed item is neither completed nor failed within the claim timeout
- **THEN** it becomes available to be claimed again

### Requirement: Cancelling pending items
The system SHALL allow a pending delete item to be cancelled before it is claimed, and SHALL NOT cancel an item that is already claimed.

#### Scenario: Cancel before claim
- **WHEN** a pending delete item is cancelled
- **THEN** it is never offered to consumers

#### Scenario: Cancel after claim
- **WHEN** a delete item is already claimed
- **THEN** cancelling it has no effect and consumers must verify the content still has no locations before deleting
