# Proposal

## Why

The directory scanner enqueues new content on the process queue, but nothing consumes that queue yet, so documents are discovered and never analysed. Extracting text is the first processing step and is a prerequisite for search.

## What Changes

- Add a background consumer that claims items from the process queue and extracts text from the content's file.
- For PDF files, first read the embedded text layer. If it contains text, use it.
- If the PDF has no usable text layer, render its pages and run OCR (Tesseract, called through the Java Foreign Function & Memory API). OCR languages are configurable and default to `en`.
- Store the extracted text and how it was obtained (text layer or OCR) per content.
- A PDF that yields no text at all is not an error: the item completes and the content is recorded with empty text.
- For image files (`png`, `jpg`, `jpeg`, `gif`, `bmp`, `tif`, `tiff`), run OCR directly with the same configurable languages.
- File types are recognised by file extension (case-insensitive). Content of any other type is completed without extraction.
- Failures (unreadable or corrupt file, OCR engine failure) use the existing queue retry/failure handling.
- Add configuration for the consumer (enabled flag, poll interval, OCR languages, tessdata path, native library path, render DPI).

## Capabilities

### New Capabilities
- `text-extraction`: Consuming the process queue and turning PDF and image content into stored plain text, using the embedded text layer with OCR as fallback for PDFs and OCR for images.

### Modified Capabilities
<!-- None: work-queues and directory-scanning requirements are unchanged; this change only adds a consumer. -->

## Impact

- New package `com.pw999.jsmd.extract` (consumer, PDF text reader, OCR service, repository, properties).
- New Flyway migration `V3` adding a `content_text` table.
- New dependencies: Apache PDFBox (text and page rendering) only. Tesseract is bound directly with FFM (no JNA/Tess4J).
- Runtime requirement: the native Tesseract library (5.x) and tessdata language files must be installed on the host or image, and the JVM must run with `--enable-native-access=ALL-UNNAMED`.
- `application.yaml` gains `jsmd.extract.*` settings with `JSMD_EXTRACT_*` environment overrides.
- Out of scope: other formats (Office documents, plain text, ...), indexing/search, UI display of text, the delete queue consumer.
