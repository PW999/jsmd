# Spec Delta

## Purpose

Provides the common page layout (top banner, left menu, functional content area) that every part of the application is rendered in, so new features plug into a consistent, simple UI.

## ADDED Requirements

### Requirement: Consistent page layout
The system SHALL render every page with a banner across the top, a menu on the left and a content area filling the remaining space.

#### Scenario: Layout on landing page
- **WHEN** an authenticated user opens the application root
- **THEN** the page shows a top banner, a left-hand menu and a content area containing the landing content

### Requirement: Banner shows application identity and user
The banner SHALL display the application name, the username of the signed-in administrator and a logout control.

#### Scenario: Banner content
- **WHEN** the admin user opens any page
- **THEN** the banner displays the application name, the admin username and a logout control

### Requirement: Menu navigation
The left menu SHALL list the available sections of the application, link each entry to its page, and visually mark the entry of the current page. Initially it SHALL contain a Home entry and a placeholder entry for Documents that leads to a page stating the feature is not yet available.

#### Scenario: Navigate via menu
- **WHEN** the user clicks a menu entry
- **THEN** the content area shows that section while banner and menu remain visible and the entry is marked as active

### Requirement: Unknown pages
The system SHALL show a page in the standard layout stating the page was not found when an authenticated user requests an unknown path.

#### Scenario: Unknown path
- **WHEN** an authenticated user requests a path that does not exist
- **THEN** the system responds with HTTP 404 and a not-found message inside the standard layout
