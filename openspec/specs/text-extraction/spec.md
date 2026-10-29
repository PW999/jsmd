# Text Extraction Specification

## Purpose

Extract plaintext from structured and unstructured PDF files and images so in a later phase we can search for documents.

## Requirements

### Requirement: Process queue consumption
The system SHALL periodically claim items from the process queue and extract text from the referenced content, marking each item done on success and failed on error so the queue's retry rules apply. Consumption SHALL be switchable by configuration, and several instances sharing one database SHALL NOT process the same item at once.

#### Scenario: Pending item processed
- **WHEN** an item is pending on the process queue and consumption is enabled
- **THEN** it is claimed, its text is extracted and stored, and the item is marked done

#### Scenario: Consumption disabled
- **WHEN** extraction is disabled by configuration
- **THEN** no items are claimed from the process queue

#### Scenario: Extraction error
- **WHEN** extraction throws an error for a claimed item
- **THEN** the item is marked failed with the error message and retried per the queue rules

### Requirement: Locating the file
The system SHALL read the content from any currently known path of that content that exists on disk, is readable, and whose bytes still match the content's recorded SHA-256. If no known path qualifies, the item SHALL be treated as a failure.

#### Scenario: Several locations
- **WHEN** a content has several known paths and the first one is missing
- **THEN** another existing path is used

#### Scenario: No readable location
- **WHEN** none of a content's known paths can be read
- **THEN** the item is marked failed

#### Scenario: File changed since scan
- **WHEN** the only known path of a content now holds different bytes than the content's recorded SHA-256
- **THEN** no text is stored for that content and the item is marked failed

#### Scenario: One location changed
- **WHEN** a content has two known paths and only the second still matches the recorded SHA-256
- **THEN** the second path is used

### Requirement: PDF text layer extraction
The system SHALL read the embedded text of each page of a PDF. A page whose text contains at least one non-whitespace character SHALL use that text and SHALL NOT be OCR'd. If every page has such text, the combined text SHALL be stored with source `TEXT_LAYER` and OCR SHALL NOT run.

#### Scenario: PDF with text
- **WHEN** a PDF in which every page has an embedded text layer is processed
- **THEN** the embedded text is stored with source `TEXT_LAYER` and OCR is not run

### Requirement: OCR fallback
The system SHALL run OCR on each rendered PDF page that has no text layer, using the configured OCR languages, and combine page texts in page order. If OCR contributed text the result SHALL be stored with source `OCR`. The OCR languages SHALL be configurable as a list and SHALL default to `en`. Several languages SHALL be passed to OCR together.

#### Scenario: Scanned PDF
- **WHEN** a PDF without a text layer is processed
- **THEN** each page is rendered and recognised, and the combined text is stored with source `OCR`

#### Scenario: Mixed PDF
- **WHEN** a PDF with a text layer on page 1 and scanned pages 2 and 3 is processed
- **THEN** only pages 2 and 3 are OCR'd and the stored text contains the text of all three pages in order, with source `OCR`

#### Scenario: Mixed PDF where OCR finds nothing
- **WHEN** a PDF has a text layer on some pages and OCR recognises no text on the others
- **THEN** the text-layer text is stored with source `TEXT_LAYER`

#### Scenario: Default language
- **WHEN** no OCR language is configured
- **THEN** OCR uses `en`

#### Scenario: Multiple languages
- **WHEN** the OCR languages are configured as `en` and `nl`
- **THEN** OCR recognises text using both languages

#### Scenario: Unavailable language data
- **WHEN** a configured OCR language has no installed language data
- **THEN** the OCR failure is reported as an item failure rather than as empty text

### Requirement: No text is not an error
The system SHALL treat a PDF that yields no text after both text layer extraction and OCR as successfully processed, record it with empty text and source `NONE`, and mark the item done.

#### Scenario: Blank PDF
- **WHEN** a PDF has no text layer and OCR recognises no text
- **THEN** the item is marked done and empty text with source `NONE` is stored

### Requirement: Storing extracted text
The system SHALL store at most one extracted text per content, replacing it when the content is processed again, so that reprocessing is idempotent.

#### Scenario: Reprocessed content
- **WHEN** an item for already-processed content is processed again
- **THEN** the stored text is replaced and no second row exists

### Requirement: Image OCR
The system SHALL run OCR, using the configured OCR languages, on content whose file has one of the extensions `png`, `jpg`, `jpeg`, `gif`, `bmp`, `tif` or `tiff` (case-insensitive), and store the recognised text with source `OCR`. An image that yields no text SHALL be recorded with empty text and source `NONE` and is not an error. A multi-page TIFF SHALL have all its pages recognised.

#### Scenario: Image with text
- **WHEN** a `.png` file containing text is processed
- **THEN** the recognised text is stored with source `OCR`

#### Scenario: Image without text
- **WHEN** an image in which OCR recognises nothing is processed
- **THEN** the item is marked done and empty text with source `NONE` is stored

#### Scenario: Uppercase extension
- **WHEN** a file named `SCAN.JPG` is processed
- **THEN** it is treated as an image

#### Scenario: Unreadable image
- **WHEN** a file with an image extension cannot be decoded as an image
- **THEN** the item is marked failed

### Requirement: File type by extension
The system SHALL decide how to process a file from its extension, case-insensitively: `pdf` is a PDF, the image extensions listed above are images. The extension of the path actually read is used. The file's bytes SHALL NOT be sniffed to override the extension.

#### Scenario: PDF extension
- **WHEN** a file named `report.pdf` is processed
- **THEN** it is processed as a PDF

#### Scenario: Extension decides
- **WHEN** a file named `scan.dat` starts with `%PDF-`
- **THEN** it is not processed as a PDF

### Requirement: Unsupported content types
The system SHALL complete process items for content whose extension is neither `pdf` nor a supported image extension without extracting text and without raising an error.

#### Scenario: Other file type
- **WHEN** an item refers to a file named `notes.docx`
- **THEN** the item is marked done and no text is stored
