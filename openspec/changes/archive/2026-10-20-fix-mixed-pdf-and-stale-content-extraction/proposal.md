# Proposal

## Why

Two code-review findings on text extraction are still open. A PDF that mixes pages with a text layer and scanned pages is stored with only the text-layer pages, so the scanned pages are never searchable. And extraction trusts whatever file sits at a known path, so a file overwritten after the scan has its text stored under the old content.

## What Changes

- OCR is applied per page: pages of a PDF with no text layer are OCR'd even when other pages have one, and the page texts are combined in page order.
- Extraction only uses a file whose bytes still match the content's recorded SHA-256; otherwise the item fails and is retried per the queue rules.
- **BREAKING (spec-level):** a PDF with a partial text layer is no longer stored as text-layer-only; the "stray text layer suppresses OCR" trade-off from `add-pdf-text-extraction` is reversed.

## Capabilities

### New Capabilities

### Modified Capabilities
- `text-extraction`: "PDF text layer extraction" and "OCR fallback" become per-page; "Locating the file" additionally requires the file to match the content's hash.

## Impact

- `PdfExtractor`, `PdfTextReader`, `OcrService` (per-page text and selective OCR).
- `ExtractionService`, `ContentTextRepository` (hash lookup, hash check using the scan package's `FileHasher`).
- Stored text for text-layer PDFs changes slightly (page separator) on reprocessing. Existing rows are not backfilled.
- Out of scope: `page_count` stays unpopulated, and the scan-side findings in `ScanRun` (DELETE queue consumer, size/mtime captured before hashing).
