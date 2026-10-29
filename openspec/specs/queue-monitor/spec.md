# queue-monitor Specification

## Purpose

Lets the administrator watch the process and delete queues, and clear items that failed permanently, without database access.

## Requirements

### Requirement: Queue page
The system SHALL provide a Queue page in the standard layout that shows, for each queue, the number of items per status, and a list of queue items. Each listed item SHALL show its queue, status, number of attempts, last error, when it was created, and one known file path of its content when there is one.

#### Scenario: Items listed
- **WHEN** the user opens the Queue page and the queues hold items
- **THEN** the page shows the status counts per queue and each item with its queue, status, attempts, last error, creation time and file path

#### Scenario: Empty queues
- **WHEN** the user opens the Queue page and there are no items
- **THEN** the page states that the queues are empty

#### Scenario: Large queue
- **WHEN** more items exist than the page can list
- **THEN** the page lists a limited number of items, shows the full status counts, and states that the list is truncated

### Requirement: Relevant items first
The Queue page SHALL list failed items first, then claimed, then pending items, and finally finished items, so that items needing attention are visible without scrolling.

#### Scenario: Failed items on top
- **WHEN** the queues hold pending, claimed and failed items
- **THEN** the failed items are listed before the claimed items and the claimed items before the pending items

### Requirement: Automatic refresh
The Queue page SHALL refresh itself at a regular interval so that the displayed counts and items follow the queue without user action.

#### Scenario: Queue changes while the page is open
- **WHEN** the page stays open while items are claimed, completed or fail
- **THEN** the page shows the updated state after the next refresh without the user reloading it

### Requirement: Remove a failed item
The system SHALL let the administrator remove a single permanently failed item from the Queue page. Only items with status failed SHALL be removable.

#### Scenario: Remove failed item
- **WHEN** the user removes a failed item
- **THEN** the item is deleted and no longer appears on the page

#### Scenario: Item is no longer failed
- **WHEN** the user removes an item that is not (or no longer) failed
- **THEN** the item is left untouched and the page tells the user it could not be removed

#### Scenario: Item already gone
- **WHEN** the user removes an item that no longer exists
- **THEN** nothing is changed and the page tells the user the item was not found

### Requirement: Purge failed items
The Queue page SHALL always provide a Purge button that removes all finished items (failed, done and cancelled) from all queues at once, after the user confirms. Items that are pending or claimed SHALL NOT be removed.

#### Scenario: Purge
- **WHEN** the user confirms the purge while failed, done, cancelled, pending and claimed items exist
- **THEN** all failed, done and cancelled items are deleted, the pending and claimed items remain, and the page reports how many items were removed

#### Scenario: Nothing to purge
- **WHEN** the Queue page shows no finished items, or the queues are empty
- **THEN** the Purge button is still offered and purging removes nothing and reports that 0 items were removed

#### Scenario: Button always visible
- **WHEN** the user opens the Queue page, whatever the queues contain
- **THEN** the Purge button is shown

### Requirement: Monitoring does not disturb the queues
Every read of queue items made for the Queue page SHALL use the read-uncommitted isolation level, so that displaying the queue never waits for, or holds, locks that the queue's claim, complete and fail operations depend on.

#### Scenario: Page opened while items are claimed
- **WHEN** a consumer holds an uncommitted change on a queue item and the Queue page is requested
- **THEN** the page is rendered without waiting for that transaction and the consumer is not delayed

### Requirement: Access restricted to the administrator
The Queue page and the remove and purge actions SHALL require an authenticated administrator, and the actions SHALL only be accepted as requests protected against cross-site request forgery.

#### Scenario: Unauthenticated access
- **WHEN** an unauthenticated user requests the Queue page or submits a remove or purge action
- **THEN** they are redirected to the login page and nothing is changed

#### Scenario: Action without CSRF token
- **WHEN** an authenticated user submits a remove or purge request without a valid CSRF token
- **THEN** the request is rejected and nothing is changed
