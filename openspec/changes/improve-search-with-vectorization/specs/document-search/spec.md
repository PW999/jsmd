# Spec Delta

## ADDED Requirements

### Requirement: Search mode selection
The system SHALL let the user choose how a document search query is applied by selecting a search mode: full-text or vectorized. The selected mode SHALL be part of the search request so it can be bookmarked, shared and kept while paging. The default mode SHALL be full-text when no mode is specified.

#### Scenario: Vector mode selected
- **WHEN** the user selects vectorized search and submits the query
- **THEN** the request uses the vectorized search mode

#### Scenario: Full-text mode selected
- **WHEN** the user selects full-text search and submits the query
- **THEN** the request uses the full-text search mode

#### Scenario: No mode given
- **WHEN** the user submits a search without selecting a mode
- **THEN** full-text search is used

#### Scenario: Mode kept while paging
- **WHEN** the user moves to the next or previous page of results
- **THEN** the request keeps the same mode and query

### Requirement: Full text search remains default
The system SHALL keep the existing full-text search behavior, including the `websearch_to_tsquery` syntax (words, quoted phrases, `or`, `-word` exclusion), as the default mode of the search form.

#### Scenario: Default full-text behaviour
- **WHEN** the user runs a search without opting in to vectorized search
- **THEN** the full-text matching rules and ranking apply

### Requirement: Vectorized search is opt-in
The system SHALL NOT require vectorized search, and SHALL NOT change how full-text search results are ranked, paged or snippeted when the full-text mode is used.

#### Scenario: Full-text results unchanged
- **WHEN** the user runs a full-text search
- **THEN** results are ranked and snippeted as before, with the same query syntax