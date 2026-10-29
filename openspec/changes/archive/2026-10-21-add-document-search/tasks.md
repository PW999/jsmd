# Tasks

## 1. Database

- [x] 1.1 Add `V4__content_text_search.sql` with the generated `search_vector` column (`simple`, first 500,000 characters) and the GIN index; verify with a schema integration test (next to `ContentTextSchemaIT`) that the column and index exist and that rows inserted before the migration are matched
- [x] 1.2 Test that `ContentTextRepository.upsert` keeps the vector current: a replaced text matches new words and not old words, and a text longer than 500,000 characters is stored without error

## 2. Search query

- [x] 2.1 Add a search result row type and a `Snippet` value that splits highlighted text on the markers into plain/highlighted segments; verify with unit tests (no markers, several matches, text containing `<`, `&` and the marker-like characters)
- [x] 2.2 Add `search(query, page, size)` and `countMatches(query)` to `DocumentRepository` using the query in design.md decision 5 (headline only on the page of ids); verify with `DocumentRepositoryIT` covering: single word, case-insensitivity, all-words, phrase, `or`, `-exclusion`, ranking order, per-path rows for identical content, files with no/empty text excluded, paging and count
- [x] 2.3 Verify in the same IT that malformed input (`"`, `& |`, `'; DROP TABLE content_text; --`) returns no rows without throwing

## 3. Web

- [x] 3.1 Extend `DocumentsController.list` with optional `q` (trimmed, max 200 characters, blank = normal list), model attributes for results, count and pager; verify with `DocumentsControllerTest` cases for blank query, matching query, no match, paging links keeping `q`, and page clamping
- [x] 3.2 Update `documents.html` with the search form (pre-filled), result table with snippet via `th:text`/`<mark>`, result count, no-match message with link back to the list, and pager links carrying `q`; add minimal `app.css` styles; verify a rendering test that `<script>` in matching text is escaped and only matched words are in `<mark>`
- [x] 3.3 Verify access control: add an unauthenticated `/documents?q=x` redirect case to `SecurityConfigTest` and an end-to-end search through `ApplicationFlowIT` (store text, log in, search, see the result link)

## 4. Docs

- [x] 4.1 Document the search behaviour (query syntax, `simple` configuration, 500,000-character limit) in `README.md` and verify the examples against a running dev instance
