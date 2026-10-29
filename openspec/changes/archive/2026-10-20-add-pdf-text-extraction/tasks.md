# Tasks

## 1. Dependencies, schema and configuration

- [x] 1.1 Add the PDFBox dependency and `--enable-native-access=ALL-UNNAMED` (surefire/failsafe `argLine` and Spring Boot run configuration) to `pom.xml` and verify `mvn -q compile` succeeds
- [x] 1.2 Add Flyway migration `V3__content_text.sql` creating `content_text` and verify with a `FlywayMigrationIT`/schema IT case that the table, primary key and `source` check constraint exist
- [x] 1.3 Add `ExtractProperties` record (enabled, poll-interval, ocr-languages default `en`, tessdata-path, tesseract-library, render-dpi) plus `jsmd.extract.*` entries in `application.yaml` with `JSMD_EXTRACT_*` overrides, and verify with a properties test covering defaults, a multi-language list and an invalid DPI

## 2. Text extraction

- [x] 2.1 Implement `FileType` (extension based, case-insensitive) and `PdfTextReader` (PDFBox text layer) and verify with unit tests: `.pdf`/`.PDF`, image extensions, `.docx` unsupported, a `.dat` file with a `%PDF-` header is unsupported; PDFs with text and blank
- [x] 2.2 Implement the `OcrEngine` interface and `TesseractOcrEngine` FFM binding (library lookup, `MethodHandle`s for the eight C API functions, grayscale image copy into a confined `Arena`, handle/text always released, clear errors for unloadable library and failed `Init3`) and verify with an engine IT that recognises a generated text image and repeats create/recognise/dispose in a loop without crashing, skipped when the library cannot be loaded
- [x] 2.3 Implement `OcrService` (page-at-a-time PDF rendering at configured DPI, ISO 639-1 to Tesseract code mapping, `+`-joined languages, tessdata `.traineddata` check when a path is configured) and verify with unit tests using a fake `OcrEngine` for language mapping, missing-language failure and page joining, plus an OCR test on a generated image-only PDF, skipped when Tesseract is not installed
- [x] 2.4 Implement `PdfExtractor` combining both: text layer first, OCR only when empty, `NONE` with empty text when OCR finds nothing; verify with tests for `TEXT_LAYER` (OCR not invoked), `OCR`, and `NONE` outcomes
- [x] 2.5 Implement `ImageExtractor` (`ImageIO`, multi-page TIFF, shared `OcrService`) and verify with tests: PNG with text, blank image, multi-page TIFF, undecodable `.png` throws

## 3. Storage and consumer

- [x] 3.1 Implement `ContentTextRepository` (upsert, NUL stripping, lookup of a content's file paths) and verify with an IT that reprocessing replaces the row and a NUL-containing text is stored
- [x] 3.2 Implement `ExtractionService.processNext()` (claim, locate first readable path, extract, store, complete; fail on exception; complete without storing for unsupported types) and verify with an IT covering: success, no-readable-path failure, extraction exception retry, unsupported type done, image OCR'd, blank PDF done with `NONE`
- [x] 3.3 Implement `ExtractionScheduler` (`@Scheduled` fixed delay, drains queue, honours `enabled`) and verify with an IT that a scan-enqueued PDF ends up with stored text, and that nothing is claimed when disabled

## 4. Operations and documentation

- [x] 4.1 Document Tesseract 5.x/tessdata installation, the native-access JVM flag and the `JSMD_EXTRACT_*` settings (README or existing docs) and verify the documented variables match `application.yaml`
- [x] 4.2 Run `mvn verify` and confirm all unit and integration tests pass (native OCR tests either pass or are explicitly skipped for missing Tesseract)
