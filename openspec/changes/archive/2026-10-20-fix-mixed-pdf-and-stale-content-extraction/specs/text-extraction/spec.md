## MODIFIED Requirements

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
