# documents-browser Specification

## Purpose

Lets the administrator browse every indexed file, read the plaintext extracted from it, and download the original file, so the results of scanning and text extraction are visible in the application.

## Requirements

### Requirement: Document list
The system SHALL provide a Documents page listing every known file, one row per known path, ordered by path. Each row SHALL show the file name, its full path, file size and extraction state. The list SHALL be paginated.

#### Scenario: Files listed
- **WHEN** the user opens the Documents page and files are known
- **THEN** each known path appears as a row with its file name, path, size and extraction state

#### Scenario: Several paths with identical content
- **WHEN** two known paths point at the same content
- **THEN** both paths appear as separate rows

#### Scenario: No files
- **WHEN** the user opens the Documents page and no files are known
- **THEN** the page states that no documents have been indexed yet

#### Scenario: More files than fit on a page
- **WHEN** more files are known than fit on one page
- **THEN** the page shows the first page of rows with controls to move to the previous and next page

### Requirement: Extraction state
The system SHALL show for each file whether its text was taken from the PDF text layer, obtained by OCR, found to contain no text, or has not been extracted yet.

#### Scenario: Not yet extracted
- **WHEN** a file's content has no stored text
- **THEN** its row shows that extraction is pending

#### Scenario: Extracted via OCR
- **WHEN** a file's text was obtained by OCR
- **THEN** its row shows that the text came from OCR

### Requirement: Document detail page
Clicking a file name in the list SHALL open a page in the standard layout showing the file name, path, size, extraction state, page count when known, and the stored plaintext with its line breaks preserved.

#### Scenario: View extracted text
- **WHEN** the user clicks a file name in the list
- **THEN** a page opens showing the file's metadata and its extracted plaintext

#### Scenario: No text available
- **WHEN** the user opens a file whose text is empty or not yet extracted
- **THEN** the page states that no text is available instead of showing an empty body

#### Scenario: Text containing markup
- **WHEN** the extracted text contains characters such as `<` or `&`
- **THEN** they are displayed literally and not interpreted as markup

#### Scenario: Unknown document
- **WHEN** the user opens a document that does not exist
- **THEN** the system responds with HTTP 404 and the standard not-found page

### Requirement: Download original file
Next to each file name, on both the list and the detail page, the system SHALL show a download icon that downloads the original file with its original file name and a content type matching its extension.

#### Scenario: Download from list
- **WHEN** the user clicks the download icon of a file in the list and the file is available
- **THEN** the browser downloads the original file with its original name

#### Scenario: Download from detail page
- **WHEN** the user clicks the download icon on the detail page
- **THEN** the same original file is downloaded

#### Scenario: Opening the file name does not download
- **WHEN** the user clicks the file name rather than the icon
- **THEN** the detail page opens and nothing is downloaded

### Requirement: Safe download
The system SHALL serve only files whose path is recorded for the requested document, and SHALL refuse the download when the file no longer exists, is not a readable regular file, or no longer matches the SHA-256 recorded for its content. In those cases the user SHALL be told why in the standard layout and no file bytes are sent.

#### Scenario: File removed from disk
- **WHEN** the user downloads a document whose file has been deleted since the last scan
- **THEN** the response is HTTP 404 with a message that the file is no longer available

#### Scenario: File changed since scan
- **WHEN** the file on disk no longer matches the recorded SHA-256
- **THEN** the download is refused with a message that the file has changed since it was indexed

#### Scenario: Symbolic link
- **WHEN** the recorded path is a symbolic link
- **THEN** the download is refused

#### Scenario: Arbitrary path requested
- **WHEN** a request supplies anything other than the id of a known document
- **THEN** no file outside the recorded paths is ever served

### Requirement: Access restricted to the administrator
All document pages and downloads SHALL require an authenticated administrator.

#### Scenario: Unauthenticated access
- **WHEN** an unauthenticated user requests the document list, a detail page or a download
- **THEN** they are redirected to the login page and no document data or file is returned
