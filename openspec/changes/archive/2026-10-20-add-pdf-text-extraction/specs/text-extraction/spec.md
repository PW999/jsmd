## ADDED Requirements

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
The system SHALL read the content from any currently known path of that content that exists on disk. If no known path exists or is readable, the item SHALL be treated as a failure.

#### Scenario: Several locations
- **WHEN** a content has several known paths and the first one is missing
- **THEN** another existing path is used

#### Scenario: No readable location
- **WHEN** none of a content's known paths can be read
- **THEN** the item is marked failed

### Requirement: PDF text layer extraction
The system SHALL first read the embedded text of a PDF. If the text contains at least one non-whitespace character, the system SHALL store it as the content's text with source `TEXT_LAYER` and SHALL NOT run OCR.

#### Scenario: PDF with text
- **WHEN** a PDF with an embedded text layer is processed
- **THEN** the embedded text is stored with source `TEXT_LAYER` and OCR is not run

### Requirement: OCR fallback
The system SHALL run OCR on the rendered pages of a PDF whose text layer is empty, using the configured OCR languages, and store the recognised text with source `OCR`. The OCR languages SHALL be configurable as a list and SHALL default to `en`. Several languages SHALL be passed to OCR together.

#### Scenario: Scanned PDF
- **WHEN** a PDF without a text layer is processed
- **THEN** each page is rendered and recognised, and the combined text is stored with source `OCR`

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
