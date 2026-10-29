# Proposal

## Why

Extracted text is stored in PostgreSQL and files can be listed and read one by one, but there is no way to find a document by what it says. Searching the text is the purpose the scanning and extraction phases were built for.

## What Changes

- Add a search box to the Documents page. Submitting a query lists the files whose extracted text matches, best match first, each with a highlighted snippet of the matching text.
- Search uses PostgreSQL full text search: an indexed `tsvector` over `content_text.text` queried with `websearch_to_tsquery` (plain words, `"quoted phrases"`, `or`, `-excluded`), so no query syntax can cause an error.
- Results are per file path (like the list), paginated at 50, and link to the existing detail and download actions.
- The query is carried in the URL (`/documents?q=...`) so results can be bookmarked and paging keeps the query. An empty query shows the normal list.
- New migration adds the search column and a GIN index; existing rows are indexed by the migration itself.
- The detail page opened from a result is unchanged.

Assumptions (recorded, not asked):
- The `simple` text search configuration is used, i.e. case-insensitive word matching without stemming or stop words. Documents may be English or Dutch (OCR languages are configurable), and a language-specific configuration would silently miss matches in the other language. Stemming can be added later.
- Only extracted text is searched, not file names or paths.
- Only the first 500,000 characters of a document's text are indexed, because PostgreSQL rejects a `tsvector` larger than 1 MB.
- Search is a section of the existing Documents page, not a new menu entry.

## Capabilities

### New Capabilities
- `document-search`: Searching the extracted text of indexed files with PostgreSQL full text search and viewing ranked results with snippets.

### Modified Capabilities

None. The plain list behaviour in `documents-browser` is unchanged when no query is given.

## Impact

- Code: new `DocumentSearchRepository` (or an extension of `DocumentRepository`) in `document`, `DocumentsController.list` gains an optional `q`, `documents.html` gains the search form and result rendering, `app.css` gets small additions.
- Database: new Flyway migration `V4__content_text_search.sql` (generated `tsvector` column + GIN index). Rewrites `content_text` once on migration; later upserts maintain the column automatically.
- No new dependencies; all access stays behind the admin login and takes the query only as a bind parameter.
- Tests: repository integration tests against Testcontainers Postgres, controller tests, migration/schema test.
