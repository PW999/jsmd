# Spec Delta

## Purpose

Lets the administrator find indexed files by semantic similarity of their extracted text using vector embeddings, as an alternative to the existing keyword full-text search.

## ADDED Requirements

### Requirement: Vectorized search mode on the Documents page
The Documents page SHALL contain a control that lets the administrator choose between full-text search and vectorized search. Submitting the form SHALL request the same page with the query as the `q` parameter and the mode as the `mode` parameter (`fts` or `vector`). When no mode is given, full-text search SHALL be used. The control SHALL reflect the current mode.

#### Scenario: Mode toggle shown
- **WHEN** the user opens the Documents page
- **THEN** a control for choosing full-text or vectorized search is shown next to the search field

#### Scenario: Default mode
- **WHEN** the user opens the Documents page without a `mode` parameter
- **THEN** full-text search is the active mode

#### Scenario: Vector mode kept in the control
- **WHEN** the user views results in vectorized mode
- **THEN** the mode control reflects vectorized search

### Requirement: Vectorized matching
The system SHALL return the files whose extracted text embedding is most similar to the embedding of the query, using cosine similarity. Results SHALL be ordered by similarity, best match first, with ties ordered by path. Matching SHALL be case-insensitive and SHALL not depend on word boundaries or stemming.

#### Scenario: Single concept
- **WHEN** the user searches for "invoice" in vectorized mode and a document's text discusses "a request for payment"
- **THEN** that document is in the results

#### Scenario: Best match first
- **WHEN** two documents match and one is more similar to the query
- **THEN** the more similar document is listed first

#### Scenario: Several paths with identical content
- **WHEN** two known paths point at the same matching content
- **THEN** both paths appear as separate results

#### Scenario: Case and stemming
- **WHEN** the user searches for "Invoices" and a document contains "invoice"
- **THEN** that document is in the results

### Requirement: Vectorized result content
Each result SHALL show the file name linking to the detail page, the download icon, the path, and the size. A snippet of the text SHALL NOT be shown in vectorized mode, because there is no term match to highlight.

#### Scenario: No snippet
- **WHEN** a document matches in vectorized mode
- **THEN** its result shows the file name, download icon, path and size but no text snippet

### Requirement: Result pagination and count for vectorized search
Vectorized results SHALL be paginated, with controls to move to the previous and next page that keep the query and the mode. The page SHALL state the number of matching files. When nothing matches the page SHALL say so and offer a way back to the full list.

#### Scenario: More results than fit on a page
- **WHEN** more than one page of files matches in vectorized mode
- **THEN** the first page is shown with next and previous controls whose links include the same query and mode

#### Scenario: No match
- **WHEN** no document matches the query in vectorized mode
- **THEN** the page states that no documents match the query

#### Scenario: Result count
- **WHEN** three files match
- **THEN** the page states that 3 files match

### Requirement: Documents without a vector are excluded
Files whose text has not been embedded (no stored vector) SHALL never appear in vectorized search results, even if their text would otherwise be relevant.

#### Scenario: Not yet embedded
- **WHEN** a file's content has no stored vector
- **THEN** it never appears in vectorized search results

#### Scenario: Embedding after upgrade
- **WHEN** a document is processed after this feature is introduced
- **THEN** its vector is stored and it is searchable in vectorized mode

### Requirement: Vectorized search access restricted to the administrator
Vectorized searching SHALL require an authenticated administrator, and the query text SHALL only ever be used as data, never as part of a database statement.

#### Scenario: Unauthenticated vectorized search
- **WHEN** an unauthenticated user requests `/documents?q=anything&mode=vector`
- **THEN** they are redirected to the login page and no result data is returned

### Requirement: Vectorized search unavailable
When the embedding service is unavailable, vectorized search SHALL respond normally with no results and no error, and the mode control SHALL remain available.

#### Scenario: Embedding service down
- **WHEN** the Ollama embedding service cannot be reached and the user searches in vectorized mode
- **THEN** the page shows no results without raising an error