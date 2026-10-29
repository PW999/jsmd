# Spec Delta

## Purpose

Lets the administrator find indexed files by the words in their extracted text, with results ranked by relevance and shown with the matching passage.

## ADDED Requirements

### Requirement: Search form on the Documents page
The Documents page SHALL contain a search form with a text field and a submit button. Submitting it SHALL request the same page with the entered text as the `q` parameter. The field SHALL be pre-filled with the current query.

#### Scenario: Search form shown
- **WHEN** the user opens the Documents page
- **THEN** a search field and button are shown above the list

#### Scenario: Query kept in the field
- **WHEN** the user views results for "invoice"
- **THEN** the search field contains "invoice"

#### Scenario: Blank query
- **WHEN** the query is empty or only whitespace
- **THEN** the normal document list is shown, not search results

### Requirement: Full text matching
The system SHALL return the files whose extracted text matches the query using PostgreSQL full text search. Matching SHALL be case-insensitive and word based. Plain words SHALL all have to match, `or` SHALL match either side, a quoted phrase SHALL match the words adjacent and in order, and a word prefixed with `-` SHALL exclude documents containing it.

#### Scenario: Single word
- **WHEN** the user searches for "invoice" and a document's text contains "Invoice"
- **THEN** that document is in the results

#### Scenario: Several words
- **WHEN** the user searches for `red bicycle`
- **THEN** only documents containing both words are returned

#### Scenario: Phrase
- **WHEN** the user searches for `"red bicycle"`
- **THEN** only documents containing those words next to each other in that order are returned

#### Scenario: Alternative and exclusion
- **WHEN** the user searches for `cat or dog -fish`
- **THEN** documents containing "cat" or "dog" and not "fish" are returned

#### Scenario: Malformed query
- **WHEN** the user enters unbalanced quotes or only punctuation such as `"` or `& |`
- **THEN** the page responds normally with no results and no error

#### Scenario: Text from OCR
- **WHEN** a document's text was obtained by OCR
- **THEN** it is searchable in the same way as text from a text layer

#### Scenario: Files without text
- **WHEN** a file has no stored text, empty text or has not been extracted yet
- **THEN** it never appears in search results

### Requirement: Result ranking and content
Results SHALL be ordered by relevance, best match first, with ties ordered by path. Each result SHALL show the file name linking to the detail page, the download icon, the path, the size, and a snippet of the text around the match with the matched words emphasised. Text in snippets SHALL be displayed literally and never interpreted as markup.

#### Scenario: Best match first
- **WHEN** two documents match and one contains the query words more often
- **THEN** that document is listed first

#### Scenario: Snippet with highlight
- **WHEN** a document matches "invoice"
- **THEN** its result shows a short excerpt of its text with "invoice" emphasised

#### Scenario: Markup in matching text
- **WHEN** the matching text contains `<script>` or `&`
- **THEN** the snippet shows those characters literally and only the matched words are emphasised

#### Scenario: Several paths with identical content
- **WHEN** two known paths point at the same matching content
- **THEN** both paths appear as separate results

### Requirement: Result pagination and count
Results SHALL be paginated, with controls to move to the previous and next page that keep the query. The page SHALL state the number of matching files. When nothing matches the page SHALL say so and offer a way back to the full list.

#### Scenario: More results than fit on a page
- **WHEN** more than one page of files matches
- **THEN** the first page is shown with next and previous controls whose links include the same query

#### Scenario: No match
- **WHEN** no document matches the query
- **THEN** the page states that no documents match the query

#### Scenario: Result count
- **WHEN** three files match
- **THEN** the page states that 3 files match

### Requirement: Search stays current
Text stored by extraction, including text that replaces earlier text for the same content, SHALL be searchable without any separate indexing step, and text that was already stored when search was introduced SHALL be searchable after upgrade.

#### Scenario: Newly extracted document
- **WHEN** extraction stores text for a file
- **THEN** a search for a word in that text finds the file

#### Scenario: Re-extracted document
- **WHEN** the stored text of a content item is replaced
- **THEN** searches match the new text and no longer match words only in the old text

#### Scenario: Existing data after upgrade
- **WHEN** the application is upgraded on a database that already holds extracted text
- **THEN** that text is searchable immediately

### Requirement: Very long text
The system SHALL store and index text of any length without failing extraction. Words beyond the first 500,000 characters of a document MAY be absent from search.

#### Scenario: Huge document
- **WHEN** extraction stores text longer than 500,000 characters
- **THEN** storing succeeds and words in the first 500,000 characters are searchable

### Requirement: Search access restricted to the administrator
Searching SHALL require an authenticated administrator, and the query text SHALL only ever be used as data, never as part of a database statement.

#### Scenario: Unauthenticated search
- **WHEN** an unauthenticated user requests `/documents?q=anything`
- **THEN** they are redirected to the login page and no result data is returned

#### Scenario: SQL metacharacters in query
- **WHEN** the query contains quotes, semicolons or SQL keywords
- **THEN** they are treated as search text and no statement is altered
