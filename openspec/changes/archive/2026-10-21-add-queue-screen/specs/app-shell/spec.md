# Spec Delta

## MODIFIED Requirements

### Requirement: Menu navigation
The left menu SHALL list the available sections of the application, link each entry to its page, and visually mark the entry of the current page. It SHALL contain a Home entry, a Documents entry that leads to the document list, and a Queue entry that leads to the queue page. The Documents entry SHALL also be marked as active on document detail pages.

#### Scenario: Navigate via menu
- **WHEN** the user clicks a menu entry
- **THEN** the content area shows that section while banner and menu remain visible and the entry is marked as active

#### Scenario: Documents entry
- **WHEN** the user clicks the Documents menu entry
- **THEN** the content area shows the document list

#### Scenario: Queue entry
- **WHEN** the user clicks the Queue menu entry
- **THEN** the content area shows the queue page and the Queue entry is marked as active

#### Scenario: Active entry on detail page
- **WHEN** the user views a document's detail page
- **THEN** the Documents menu entry is marked as active
