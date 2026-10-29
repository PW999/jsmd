# Proposal

## Why

Files are scanned, OCR'd and their text stored in PostgreSQL, but nothing in the UI shows the result: the Documents page is a "not yet available" placeholder. Users need to see what has been indexed, read the extracted text, and get back to the original file.

## What Changes

- Replace the Documents placeholder with a paginated list of every known file (one row per `file_location`), showing file name, path, file type, size and extraction state (extracted via text layer / OCR / no text / not yet processed).
- Add a document detail page, opened by clicking a file name, showing the file's extracted plaintext and its metadata.
- Add a download icon next to each file name (list and detail page) that downloads the original file from disk.
- Download validates the file before serving: it must still exist, be readable, be a regular file (no symlinks) and still match the recorded SHA-256; otherwise the user sees an explanatory error instead of wrong bytes.
- Update the Menu navigation requirement: the Documents entry now leads to the documents list rather than a placeholder.

Assumptions (recorded, not asked): the list is per file path, so identical content at several paths appears once per path; the list is sorted by path and paginated at 50 rows; no search/filter is included (search is a later phase per the text-extraction spec).

## Capabilities

### New Capabilities
- `documents-browser`: Listing indexed files, viewing a file's extracted text, and downloading the original file.

### Modified Capabilities
- `app-shell`: The Documents menu entry leads to the real documents list instead of a "not yet available" placeholder.

## Impact

- Code: `web/DocumentsController` (list, detail, download endpoints), a new read-side query repository joining `file_location`, `content` and `content_text`, Thymeleaf templates `documents.html` and a new detail template, `app.css`.
- Security: all new routes sit behind the existing admin login; download serves only paths stored in the database, never user-supplied paths.
- No schema migration and no new dependencies.
- Tests: MockMvc/integration tests for list, detail, download and error cases; existing placeholder assertions must be updated.
