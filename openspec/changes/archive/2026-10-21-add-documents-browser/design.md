# Design

## Context

The schema already holds everything needed: `file_location` (path, size, `content_id`), `content` (SHA-256) and `content_text` (text, source, page_count). `DocumentsController` is a placeholder rendering `documents.html`. The UI is server-rendered Thymeleaf inside `layout.html`, data access uses `JdbcClient` repositories, and every route except `/login` and `/css/**` already requires the admin login. `ContentTextRepository.findSha256` exists and `PdfTextReader`/`ExtractionService` already verify the hash when reading a file for extraction. `FileType` classifies by extension only. See proposal.md for scope and assumptions.

## Goals / Non-Goals

**Goals:**
- Reuse the existing stack: server-rendered pages, no JS framework, no new dependencies, no migration.
- Make download safe by construction: only DB-recorded paths, verified before serving.

**Non-Goals:**
- Search, filtering, sorting controls, deleting or re-processing documents, in-browser PDF/image preview.
- Streaming files outside the configured scan roots or serving files for content that has no known path.

## Decisions

**1. A "document" is a `file_location` row, addressed by its id** (`/documents`, `/documents/{id}`, `/documents/{id}/download`).
Alternative: key by `content_id`. Rejected: one content can live at several paths, which makes "the original file name" and "which path to download" ambiguous. Ids are never accepted as paths, so there is no path-traversal surface.

**2. New read-side repository `DocumentRepository` in `web` (or a `document` package) using `JdbcClient`.**
`LEFT JOIN content_text` so unprocessed files still appear. The list query selects only metadata (never `text`), with `ORDER BY path, id LIMIT ? OFFSET ?` plus a `count(*)`; the detail query loads the text for one row. Alternative: extend `FileIndex`/`ContentTextRepository`; rejected to keep the scanner/extractor write-side classes untouched. Offset pagination is adequate for an admin tool; keyset pagination would be over-engineering. Page size is fixed at 50, with `page` as a 0-based request parameter clamped to valid values.

**3. Extraction state is derived, not stored.** `content_text` row absent → PENDING; otherwise its `source` (TEXT_LAYER, OCR, NONE). A file whose process item permanently FAILED also shows as pending; surfacing failures is a follow-up. File name is derived from the path in the view model (`Path.getFileName`).

**4. Text rendering with `th:text` inside `<pre>`** (CSS `white-space: pre-wrap`) so line breaks are kept and markup is escaped by Thymeleaf. Very large text is shown in full; no truncation in this change.

**5. Download via a `DocumentDownloadService` that verifies, then streams.**
Steps: load the location and its content's SHA-256 → `Files.isRegularFile(path, NOFOLLOW_LINKS)` and readable → stream the file through a `MessageDigest` and compare with the recorded hash → respond with `ResponseEntity<Resource>` (`Content-Disposition: attachment` with the original name via `ContentDisposition`, content type from extension: `application/pdf`, `image/*`, fallback `application/octet-stream`, plus `X-Content-Type-Options: nosniff`, already a Spring Security default). Failures are mapped to exceptions (`DocumentNotFoundException` → 404, `FileUnavailableException` → 404 "no longer available", `FileChangedException` → 409 "changed since indexed") rendered by one `@ControllerAdvice`/`@ResponseStatus` error view in the standard layout.
Alternative: skip the hash check for speed. Rejected: the spec guarantees the user receives the file that was indexed, and a hash pass over a local file is cheap relative to the transfer. Reuse the existing `scan.FileHasher` (`hash(Path)` returns a `ContentHash` with the digest) rather than writing a second hasher. Verification and streaming read the file twice; a swap between the two is accepted as a negligible race for a single-admin tool.

**6. Detail page and download icon markup.** The file name is an `<a>` to the detail page; the icon is a separate `<a class="download" download>` with an inline SVG and `aria-label="Download <name>"`/`title`, so the two actions are distinct targets and no icon font or asset is needed. The menu fragment's `active` model attribute is set to `documents` by every controller method here.

**7. Error pages.** The existing 404 template stays for unknown routes. A new `error/document-unavailable` style view (or a model-driven message in the existing layout) carries the message and status for the download failures and for an unknown document id.

## Risks / Trade-offs

- [Large `text` column slows the list] → list query never selects `text`.
- [Hashing a very large PDF delays the download start] → acceptable for local files; revisit with caching if it bites.
- [Indexed file replaced on disk before the next scan] → hash check refuses with a clear message instead of serving different bytes.
- [Offset pagination drifts if a scan runs between page loads] → acceptable; at worst a row is skipped or repeated.
- [Stale rows for files deleted on disk until the next scan] → they remain listed; download reports "no longer available".
