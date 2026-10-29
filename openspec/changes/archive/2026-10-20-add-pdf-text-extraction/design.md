# Design

## Context

The scanner (see `directory-scanning` and `work-queues` specs) enqueues new content on the `PROCESS` queue, keyed by `content.id`. `WorkQueue` offers `claim`, `complete` and `fail` with at-least-once delivery, retries and stale-claim recovery, but has no consumer. A content may have several rows in `file_location`; the bytes live only on disk. The app is Spring Boot on Java 27, Postgres via `JdbcClient` and Flyway (latest migration `V2`), configuration via `@ConfigurationProperties` records with `JSMD_*` env overrides, and a `@Scheduled` + exclusive-lock pattern in the scan package. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- A consumer that drains the process queue and stores text per content.
- Cheap text-layer path first, OCR only as fallback.
- Idempotent processing, since delivery is at-least-once.

**Non-Goals:**
- Other formats (Office, plain text, WebP, ...), text cleaning/normalisation, language detection, search indexing, UI.
- Per-page storage or word coordinates.
- Parallel OCR within a document.

## Decisions

**Libraries: PDFBox + FFM bindings.** PDFBox (`PDFTextStripper`) reads the text layer and `PDFRenderer` renders pages to `BufferedImage` for OCR, so one library covers both. Tesseract is called directly through the Foreign Function & Memory API (final since Java 22, so no preview flag) against the Tesseract C API (`capi.h`). Alternatives: Tess4J/JNA (extra third-party wrapper with a heavy transitive tree that overlaps PDFBox); shelling out to `tesseract` (process management, temp files, parsing). The C API surface needed is small: `TessBaseAPICreate`, `TessBaseAPIInit3`, `TessBaseAPISetImage`, `TessBaseAPISetSourceResolution`, `TessBaseAPIGetUTF8Text`, `TessDeleteText`, `TessBaseAPIEnd`, `TessBaseAPIDelete`. Raw pixels are passed to `SetImage`, so Leptonica needs no bindings of its own. Native Tesseract 5.x and tessdata must be installed; this is documented and checked at first use.

**Text layer decision rule.** The text layer is used when the extracted text contains any non-whitespace character. Rationale: simple and predictable per the requirement. Trade-off: a scanned PDF with a tiny stray text layer (e.g. a scanner watermark) will not be OCR'd. A minimum-characters threshold can be added later without changing the spec's structure.

**OCR engine and FFM binding.** `OcrEngine` is an interface (`String recognise(BufferedImage image)`-style, one session per extraction run) with `TesseractOcrEngine` as the FFM implementation; `OcrService` uses it, and service/consumer tests substitute a fake. `TesseractOcrEngine` loads the library once (`SymbolLookup.libraryLookup`) from `jsmd.extract.tesseract-library` (default `libtesseract.so.5`, `tesseract` on Windows, `libtesseract.5.dylib` on macOS) and builds `MethodHandle`s with `Linker.nativeLinker()`. Each OCR session creates one API handle (handles are not thread-safe), initialises it with the tessdata path and `+`-joined languages, and always ends and deletes it in `finally`. Images are converted to 8-bit grayscale, copied into a confined `Arena` segment and passed to `SetImage` with the DPI; the returned `char*` is copied to a `String` and released with `TessDeleteText`. A non-zero `Init3` result, or a library that cannot be loaded, raises an exception with a message naming the library path or language so the item fails clearly. The JVM must start with `--enable-native-access=ALL-UNNAMED` (set in the Dockerfile/run configuration and in surefire/failsafe `argLine`), otherwise Java warns on first native access.

**OCR flow.** Render each page at a configurable DPI (default 300), recognise it with one engine session per run, join pages with a form-feed/newline separator. Languages are a list (`jsmd.extract.ocr-languages`, default `en`) joined with `+` for Tesseract (`eng+nld`). Tesseract uses three-letter codes, so a small mapping covers two-letter ISO 639-1 codes (`en`→`eng`, `nl`→`nld`, ...) and three-letter codes pass through unchanged. Pages are rendered and recognised one at a time to bound memory.

**Missing language data is a failure.** `Init3` returns non-zero when language data is missing, which is raised as an error; as an extra guard, when `tessdata-path` is set the service also checks that `<tessdata>/<lang>.traineddata` exists for every configured language and names the missing one. Otherwise a missing language could produce empty text, which the spec treats as success and would silently lose documents.

**Storage: `content_text` table (V3).** `content_id bigint PRIMARY KEY REFERENCES content(id)`, `text text NOT NULL`, `source text CHECK (source IN ('TEXT_LAYER','OCR','NONE'))`, `page_count integer`, `extracted_at timestamptz`. Upsert with `ON CONFLICT (content_id) DO UPDATE` makes reprocessing idempotent. Text is kept as `text` in Postgres, which handles large values via TOAST; NUL characters (`\u0000`), which Postgres rejects, are stripped before storing. A separate table rather than a column on `content` keeps the scanner's hot table narrow.

**Consumer: scheduled poller.** An `ExtractionScheduler` with `@Scheduled(fixedDelay)` drains the queue in a loop (claim until empty or disabled), processing items sequentially in one thread. `WorkQueue.claim` already uses `FOR UPDATE SKIP LOCKED`, so multiple instances are safe without an extra lock. Each item: claim → extract → store text → `complete`; any exception → `fail(id, message)`. Storing text and `complete` are not in one transaction because `WorkQueue` calls are single statements; at-least-once with an idempotent upsert makes that safe. Alternatives: a dedicated worker thread pool (more throughput, but OCR is CPU-heavy and sequential is a safe default; concurrency can be added later), or Spring events on scan completion (not durable).

**Claim timeout vs. long OCR.** The default queue claim timeout is 15 minutes; a very large scanned PDF could exceed it and be reclaimed while still running. Result is duplicate work but correct data due to idempotency; `complete` returns false in that case and is logged. Operators can raise `JSMD_QUEUE_CLAIM_TIMEOUT`.

**File type by extension.** A small `FileType` resolver maps the lower-cased extension of the path being read to `PDF`, `IMAGE` or `UNSUPPORTED`; unsupported types complete with no stored text. No content sniffing: the extension is the contract. Image extensions: png, jpg, jpeg, gif, bmp, tif, tiff (decoded with `ImageIO`, which reads multi-page TIFF; WebP is not supported by default and is left out).

**Image OCR.** Images go straight to the same `OcrService` as rendered PDF pages (it takes `BufferedImage`s), so languages, tessdata checks and the `NONE` rule are shared. Multi-page TIFFs are read page by page via `ImageReader`. A file that `ImageIO` cannot decode throws, so the item fails.

**File location.** Query `file_location` for the content's paths, try each in turn, use the first that is readable; its extension selects the file type. (Identical bytes under different extensions are rare; the first readable path wins.) None readable → exception → `fail` (the file may be temporarily unavailable or content may be pending deletion).

**Configuration** (`jsmd.extract.*`, env `JSMD_EXTRACT_*`): `enabled` (default true), `poll-interval` (`PT10S`), `ocr-languages` (`en`), `tessdata-path` (optional; falls back to `TESSDATA_PREFIX`/Tesseract's default), `tesseract-library` (library name or absolute path), `render-dpi` (300, min 72). Validated like the other properties records.

## Risks / Trade-offs

- [Native Tesseract missing at runtime] → Fail the item with a clear message naming the missing library/data; document installation; the engine's integration test is skipped when the library cannot be loaded.
- [FFM misuse crashes the JVM] → Keep the binding in one small class, confine all native memory to an `Arena`, always release the handle and text in `finally`, and cover it with a real-library test (create/recognise/dispose repeated in a loop).
- [Tesseract ABI/soname changes] → Only the stable C API is used; library name is configurable; Tesseract 5.x is the documented requirement.
- [OCR is slow and memory-hungry for large PDFs] → Page-at-a-time rendering, sequential processing, configurable DPI.
- [Encrypted or corrupt PDFs] → PDFBox throws; the item fails and retries until attempts are exhausted, then is permanently failed with the error recorded in `last_error`. Password-protected PDFs are not supported.
- [Retrying permanent failures wastes work] → Accepted; bounded by `max-attempts`.
- [Stray text layer suppresses OCR] → See decision above; revisit with a threshold if seen in practice.
- [Stale claim during long OCR] → Idempotent upsert; documented knob.
