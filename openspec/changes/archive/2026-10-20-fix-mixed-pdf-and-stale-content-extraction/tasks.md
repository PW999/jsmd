# Tasks

## 1. Per-page text layer and selective OCR

- [x] 1.1 Add `PdfTextReader.readPages` (one string per page) and verify with a unit test on a multi-page generated PDF, including a blank page
- [x] 1.2 Add `OcrService.recognisePdfPages(path, indexes)` using one engine session and have `recognisePdf` reuse it; verify with a fake-engine unit test that only the requested pages are rendered and results keep the requested order
- [x] 1.3 Change `PdfExtractor` to OCR only blank pages and merge in page order with source rules (`TEXT_LAYER`, `OCR`, `NONE`); verify with unit tests: all-text (OCR not invoked), mixed, mixed where OCR finds nothing, all-blank, whitespace-only layer
- [x] 1.4 Add an integration test with a generated PDF of one text page and one image-only page and verify both pages' text is stored with source `OCR` (skipped when Tesseract is unavailable)

## 2. Content hash check

- [x] 2.1 Add `ContentTextRepository.findSha256` and verify with `ContentTextRepositoryIT`
- [x] 2.2 Inject `FileHasher` into `ExtractionService` and select the first readable path whose hash matches; verify with `ExtractionServiceIT` cases: changed file fails the item and stores nothing, second location matches when the first changed, unchanged file still succeeds
- [x] 2.3 Update existing ITs that insert fake hashes to use real file hashes, and verify `mvn test` and the ITs pass

## Workflow follow-up

- Run `/opsx:verify` and archive after review.
