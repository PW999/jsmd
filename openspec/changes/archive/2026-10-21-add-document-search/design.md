# Design

## Context

Text lives in `content_text(content_id, text, source, page_count, extracted_at)`, one row per content, written by `ContentTextRepository.upsert`. A document in the UI is a `file_location` row; `DocumentRepository` lists them with `JdbcClient` and `DocumentsController` renders `documents.html` with offset pagination (50 per page). Flyway migrations are at V3. OCR languages are configurable (`en` default, several allowed), so stored text may be in more than one language. See proposal.md for scope and assumptions.

## Goals / Non-Goals

**Goals:**
- Keep the index in sync automatically, whatever writes `content_text`.
- Reuse the existing list page, pagination and layout; no new dependencies.

**Non-Goals:**
- Stemming or per-document language detection, fuzzy/typo matching, searching file names, filters (type, state, date), search-as-you-type, `pg_trgm`.
- A search-specific page or API.

## Decisions

**1. Generated, stored `tsvector` column plus GIN index** (migration `V4__content_text_search.sql`):
`ALTER TABLE content_text ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple', left(text, 500000))) STORED;` and `CREATE INDEX content_text_search_idx ON content_text USING gin (search_vector);`
A generated column keeps the vector correct for every insert/update, including the upsert's `DO UPDATE`, with no change to `ContentTextRepository` and no trigger. Adding it populates existing rows. Alternative: expression index on `to_tsvector('simple', text)` with no extra column — smaller on disk, but queries must repeat the exact expression and `ts_rank` has to recompute the vector per row. Alternative: trigger — more moving parts for the same result.

**2. `simple` configuration, hard-coded in the migration and the query.** A generated column needs an immutable expression, so the configuration cannot be a runtime property without re-creating the column. `simple` is the only choice that is correct for mixed English/Dutch text. Switching to a language configuration later is a new migration.

**3. 500,000-character cap.** PostgreSQL raises "string is too long for tsvector" above 1 MB of vector, which would make `upsert` throw and the extraction item fail. Capping the input is a cheap guarantee that extraction cannot be broken by search. Words past the cap are not found; the detail page still shows the full text.

**4. Query with `websearch_to_tsquery('simple', ?)`.** It never raises a syntax error, supports quotes, `or` and `-`, and the user text is a bind parameter. An all-punctuation query yields an empty tsquery; Postgres returns no rows and logs a notice, which is the desired "no results". Alternative: `plainto_tsquery` (no phrase/or/exclude) or `to_tsquery` (errors on user input).

**5. Search query shape.** One method on `DocumentRepository`:
```sql
SELECT fl.id, fl.path, fl.size, ct.source,
       ts_rank_cd(ct.search_vector, q.query) AS rank,
       ts_headline('simple', left(ct.text, 500000), q.query,
                   'StartSel=<<, StopSel=>>, MaxFragments=2, MaxWords=30, MinWords=10') AS snippet
FROM file_location fl
JOIN content_text ct ON ct.content_id = fl.content_id
CROSS JOIN websearch_to_tsquery('simple', ?) AS q(query)
WHERE ct.search_vector @@ q.query
ORDER BY rank DESC, fl.path, fl.id
LIMIT ? OFFSET ?
```
plus a matching `count(*)`. `ts_headline` is expensive, so it is computed in an outer query over only the page of ids (rank/order in an inner subquery) rather than for every match. Text is capped identically to the index so the headline sees what was indexed.

**6. Safe snippet rendering.** `ts_headline` is given non-HTML markers (`<<` / `>>`, or private-use characters if collision with real text is a concern) and the repository returns a small `Snippet` value that splits on the markers into plain and highlighted segments. The template renders segments with `th:text`, wrapping highlighted ones in `<mark>`. No `th:utext`, so document text can never inject markup.

**7. Same URL, same controller.** `GET /documents?q=...&page=n`: blank `q` (after trim) falls through to the existing list path; otherwise the controller uses the search methods and the same `documents.html` renders a result table (name + download icon, path, size, snippet) instead of the state column. Page clamping and pager reuse the existing logic, with `q` added to pager links via Thymeleaf link params. `q` is trimmed and limited to 200 characters. No new routes, so existing security rules already cover it.

## Risks / Trade-offs

- [Migration rewrites `content_text` and builds the index, locking writes for its duration] → table is small relative to the files on disk and the app is single-admin; run at deploy time, Flyway runs it before the scheduler starts.
- [Duplicated vector storage (about the size of the text's distinct words)] → accepted for fast ranking; revisit if disk matters.
- [No stemming: "invoices" does not match "invoice"] → documented; users can use `or`. Revisit with a language configuration.
- [`ts_rank_cd` and `ts_headline` cost on very large documents] → headline limited to the result page, text capped.
- [Offset pagination on ranked results can shift if text changes between pages] → acceptable for an admin tool.

## Migration Plan

Deploy normally; Flyway applies V4 on start. Rollback is dropping the index and column (`V5` or manual), nothing else depends on them.
