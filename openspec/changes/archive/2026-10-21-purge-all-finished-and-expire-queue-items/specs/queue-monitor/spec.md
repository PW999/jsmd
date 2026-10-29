# Spec Delta

## MODIFIED Requirements

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
