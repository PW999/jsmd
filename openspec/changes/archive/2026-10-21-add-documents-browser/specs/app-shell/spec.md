# Spec Delta

## MODIFIED Requirements

### Requirement: Menu navigation
The left menu SHALL list the available sections of the application, link each entry to its page, and visually mark the entry of the current page. It SHALL contain a Home entry and a Documents entry that leads to the document list. The Documents entry SHALL also be marked as active on document detail pages.

#### Scenario: Navigate via menu
- **WHEN** the user clicks a menu entry
- **THEN** the content area shows that section while banner and menu remain visible and the entry is marked as active

#### Scenario: Documents entry
- **WHEN** the user clicks the Documents menu entry
- **THEN** the content area shows the document list

#### Scenario: Active entry on detail page
- **WHEN** the user views a document's detail page
- **THEN** the Documents menu entry is marked as active
