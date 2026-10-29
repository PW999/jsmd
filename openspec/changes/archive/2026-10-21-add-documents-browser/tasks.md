# Tasks

## 1. Data access

- [x] 1.1 Add `DocumentRepository` (`JdbcClient`) with `list(page, size)` returning rows (id, path, size, extraction state, no text) ordered by `path, id`, `count()`, and `find(id)` returning the row plus text, source, page count and the content's SHA-256; extraction state derived from `LEFT JOIN content_text`
- [x] 1.2 Add `DocumentRepositoryIT` (Testcontainers) covering ordering, paging, pending vs TEXT_LAYER/OCR/NONE state, two paths with identical content, and unknown id

## 2. Download

- [x] 2.1 Add `DocumentDownloadService` that resolves the recorded path, checks regular file / no symlink / readable, verifies SHA-256 with `FileHasher`, and returns a `Resource` plus file name and content type from the extension
- [x] 2.2 Add exceptions for unknown document (404), file unavailable (404) and file changed (409), and a controller advice that renders them in the standard layout
- [x] 2.3 Unit/integration tests: success, deleted file, modified file, symlink, content type per extension, `Content-Disposition` with original name (including non-ASCII names)

## 3. Web layer

- [x] 3.1 Replace the `DocumentsController` placeholder with `GET /documents` (page parameter clamped, page size 50), `GET /documents/{id}` and `GET /documents/{id}/download`; set `active=documents` on all
- [x] 3.2 Rewrite `documents.html`: table with file name link, path, size, extraction state, download icon, empty-state message, previous/next controls
- [x] 3.3 Add `document.html` detail page: metadata, download icon beside the name, text in `<pre>` via `th:text`, "no text available" state, back link to the list
- [x] 3.4 Add download-icon (inline SVG with accessible label), table and `pre-wrap` text styles to `app.css`
- [x] 3.5 Add the error view used for unavailable/changed/unknown document

## 4. Tests and docs

- [x] 4.1 `@WebMvcTest` for list, detail (markup escaping, no-text state, 404), download headers, error pages, and redirect to login when unauthenticated
- [x] 4.2 Update existing tests that assert the "not yet available" placeholder (`LayoutTest`, `ApplicationFlowIT`) and the Documents menu active-state assertion
- [x] 4.3 Update `README.md` to describe the Documents pages and the download checks
- [ ] 4.4 Run `mvn verify` with JDK 27 and manually check list, detail and download in the running app
