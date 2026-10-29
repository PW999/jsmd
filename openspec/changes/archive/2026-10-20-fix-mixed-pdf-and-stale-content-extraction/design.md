# Design

## Context

Extraction (`ExtractionService`) takes the first readable path from `file_location`, then `PdfExtractor` reads the whole-document text layer and only OCRs when that is blank. `content.sha256` is recorded by the scanner via `FileHasher`. See proposal.md for motivation and the archived `add-pdf-text-extraction` design for the original text-layer rule.

## Goals / Non-Goals

**Goals:**
- Never lose scanned pages of a mixed PDF.
- Never store text under a content whose bytes no longer match.

**Non-Goals:**
- Per-page storage, `page_count`, text-layer quality thresholds.
- Scan-side fixes (`ScanRun`).

## Decisions

**Per-page text layer, selective OCR.** `PdfTextReader` gains `readPages(Path)` returning one string per page (PDFBox `PDFTextStripper` with start/end page). `PdfExtractor` collects blank page indexes; if none, it returns the text layer joined with `\f\n` (the separator OCR already uses). Otherwise `OcrService.recognisePdfPages(path, indexes)` runs one engine session over just those pages, results are placed back by index. Alternative: OCR the whole PDF when any page is blank — simpler but wastes work and discards good text-layer text.

**Source label.** `OCR` if OCR added text, else `TEXT_LAYER` if any layer text, else `NONE`. The `source` check constraint has no `MIXED` value and adding one needs a migration for little gain.

**Hash check in the consumer.** `ExtractionService` loads `content.sha256` through a new `ContentTextRepository.findSha256`, then iterates paths, skipping unreadable ones and hashing the rest with `FileHasher`; the first match wins. No match throws, so the queue's retry and permanent-failure rules apply. Alternative: compare size/mtime only — cheaper but misses same-size edits. Cost: each item reads its file twice (hash plus extraction); acceptable next to OCR cost, and the file is hashed streaming.

**Residual race.** A file can change between the hash check and extraction. This is accepted: the next scan re-points the location and enqueues the new content, and reprocessing is idempotent.

## Risks / Trade-offs

- [Blank pages (genuinely empty) get OCR'd] → Cost only; OCR returns blank and the page contributes nothing.
- [Extra hashing of large files] → Streaming hash with fixed buffer; bounded by file size.
- [Stored text for text-layer PDFs differs slightly from before] → Only on reprocessing; no backfill.
