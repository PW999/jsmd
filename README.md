# jsmd

A Spring Boot web application that will read, analyze and store searchable plain-text versions of documents in a PostgreSQL database.

This foundation provides the build, database connectivity (Flyway-managed schema), admin form login and the application shell (banner, left menu, content area). Further document features are added in later changes.

## Prerequisites

- **JDK 27** (includes `javac`). The default `java` on this machine may be older, so point `JAVA_HOME` at JDK 27:

  ```bash
  export JAVA_HOME=/usr/lib/jvm/jre-27-openjdk
  ```

- **Maven** 3.9+
- **Docker** (with the Compose plugin), running. It provides the local PostgreSQL and is used by the integration tests (Testcontainers).

## Configuration

Settings live in `src/main/resources/application.yaml` and are overridden by environment variables, which take precedence.

| Environment variable   | Required | Default                                  | Purpose                                  |
|------------------------|----------|------------------------------------------|------------------------------------------|
| `JSMD_ADMIN_USERNAME`  | yes, except under `dev` | none (`admin` under `dev`)  | Username of the single admin account     |
| `JSMD_ADMIN_PASSWORD`  | yes, except under `dev` | none (`admin` under `dev`)  | Password of the admin account            |
| `JSMD_DB_URL`          | no       | `jdbc:postgresql://localhost:5432/jsmd`  | JDBC URL of the PostgreSQL database      |
| `JSMD_DB_USER`         | no       | `jsmd`                                   | Database user                            |
| `JSMD_DB_PASSWORD`     | no       | `jsmd`                                   | Database password                        |
| `PORT`                 | no       | `8080`                                   | HTTP port                                |
| `JSMD_SCAN_DIRECTORIES`    | no   | none; `./dev-documents` under `dev`      | Comma-separated directories to scan recursively; none may be inside another |
| `JSMD_SCAN_INTERVAL`       | no   | `PT5M` (`PT1M` under `dev`)              | Delay between the end of one scan and the start of the next |
| `JSMD_SCAN_ENABLED`        | no   | `true`                                   | Set to `false` to switch scanning off |
| `JSMD_SCAN_SETTLE_TIME`    | no   | `PT5S`                                   | Files modified more recently than this are left for the next scan |
| `JSMD_QUEUE_RETRY_DELAY`   | no   | `PT1M`                                   | Wait before a failed queue item is offered again |
| `JSMD_QUEUE_MAX_ATTEMPTS`  | no   | `5`                                      | Claims allowed per queue item before it fails permanently |
| `JSMD_QUEUE_CLAIM_TIMEOUT` | no   | `PT15M`                                  | Time after which an uncompleted claim is offered again |
| `JSMD_QUEUE_RETENTION`     | no   | `P7D`                                    | Queue items created longer ago than this are deleted by the daily cleanup, whatever their status; must be positive |
| `JSMD_QUEUE_CLEANUP_CRON`  | no   | `0 30 3 * * *`                           | Spring cron expression (server time zone) for the daily cleanup |
| `JSMD_EXTRACT_ENABLED`         | no   | `true`                                   | Set to `false` to stop text extraction without restarting |
| `JSMD_EXTRACT_POLL_INTERVAL`   | no   | `PT10S` (`PT1M` under `dev`)             | Delay between the end of one extraction drain pass and the start of the next |
| `JSMD_EXTRACT_OCR_LANGUAGES`   | no   | `en`                                     | Comma-separated OCR languages (ISO 639-1 or Tesseract three-letter codes, e.g. `en,nl`) |
| `JSMD_EXTRACT_TESSDATA_PATH`   | no   | system default (`TESSDATA_PREFIX`)       | Absolute path to the directory containing `.traineddata` files |
| `JSMD_EXTRACT_TESSERACT_LIBRARY` | no | `libtesseract.so.5.5`                  | Native Tesseract library name or absolute path |
| `JSMD_EXTRACT_RENDER_DPI`      | no   | `300`                                    | DPI used when rendering PDF pages for OCR (minimum 72; higher is slower but more accurate) |

There are no default admin credentials outside the `dev` profile (see [Run locally](#run-locally)): startup fails with an error naming the missing setting (`jsmd.admin.username` or `jsmd.admin.password`) if either is missing or blank and `dev` is not active. The password is BCrypt-hashed in memory and never logged.

`.env.example` lists these settings. Copy it to `.env` (git-ignored) and load it into your shell; the application does not read `.env` by itself.

## Work queues

Work for later processing is held in PostgreSQL (Flyway migration `V2__file_index_and_queues.sql`):

- `content`: one row per distinct SHA-256 hash, so identical content is stored once.
- `file_location`: every known file path, pointing at its `content`. Several paths may share one content.
- `work_item`: the two queues, `PROCESS` (new content to analyze) and `DELETE` (content that no longer has a file on disk; this records intent only). An item is `PENDING`, `CLAIMED`, `DONE`, `FAILED` or `CANCELLED`. There is at most one pending or claimed item per content per queue.

Delivery is at-least-once, so consumers must be idempotent. A failed item becomes available again after `JSMD_QUEUE_RETRY_DELAY` and fails permanently after `JSMD_QUEUE_MAX_ATTEMPTS` claims. A claim that is neither completed nor failed within `JSMD_QUEUE_CLAIM_TIMEOUT` is offered again (or fails permanently if its attempts are used up). Durations use ISO-8601 (`PT30S`, `PT5M`).

## Directory scanning

Set `JSMD_SCAN_DIRECTORIES` to one or more directories (comma-separated, none inside another; startup fails naming both if they overlap). They are scanned recursively every `JSMD_SCAN_INTERVAL`, measured from the end of the previous scan. With no directories nothing is scanned. Only one scan runs at a time, also when several instances share a database. Symbolic links are not followed or indexed (a configured directory must itself not be a symbolic link).

Files are identified by the **SHA-256** of their bytes, so identical files share one `content` row and are queued for processing only once. Each scan classifies every file:

| Result | How it is determined | Effect |
|--------|----------------------|--------|
| **new** | path unknown, hash unknown | content stored, one `PROCESS` item |
| **changed** | known path, different hash | path points at the new content (a `PROCESS` item if that hash is new); if no file has the old content any more, a `DELETE` item for it |
| **moved** | path unknown, hash matches a known file whose path is gone (same or another scanned directory) | recorded path updated, nothing queued |
| **duplicate** | path unknown, hash matches a known file that still exists | extra location of the same content, nothing queued |
| **removed** | known path no longer on disk and not explained by a move | location dropped; if it was the last one, a `DELETE` item |
| **unchanged** | known path, same size and modification time | not read or hashed |

A file that was removed and reappears while its `DELETE` item is still pending cancels that item and is not queued for processing again. If a file only had its modification time changed but its hash is the same, the stored time is updated and nothing is queued.

Safety rules: removals and moves are only evaluated for directories that were fully walked, so a directory that is missing, unmounted or unreadable leaves its files untouched. Unreadable files and directories keep their previous state. Files modified less than `JSMD_SCAN_SETTLE_TIME` ago are left for the next scan because they may still be written to.

Limitations:

- To avoid re-reading every file on every scan, an unchanged **size and modification time** is taken to mean unchanged content. An edit that preserves both is not noticed.
- The `DELETE` queue only records the intent that content is no longer used; the scanner never deletes content. A consumer must check in its own transaction that the content still has no locations before deleting it, because a file can reappear after the item was claimed.
- A scanned directory that is mounted but empty looks like every file was removed. Removed content is only queued, never deleted, so this is recoverable.

## Text extraction

When a new file is indexed by the scanner, a `PROCESS` item is placed on the work queue. The extraction consumer drains that queue every `JSMD_EXTRACT_POLL_INTERVAL` (default 10 seconds), extracting text from each file and storing the result in `content_text`.

### Supported file types

| Extension (case-insensitive) | Strategy |
|------------------------------|----------|
| `pdf` | Embedded text layer first. If the layer is blank, each page is rendered and recognised by OCR. |
| `png`, `jpg`, `jpeg`, `gif`, `bmp`, `tif`, `tiff` | OCR on each page/frame. |
| anything else | Completed without text; not an error. |

A file with no detectable text (blank PDF, blank image) is stored with empty text and source `NONE` — this is not a failure.

### OCR with Tesseract

OCR uses the native Tesseract C API through the Java Foreign Function & Memory API (FFM, final since Java 22). **Tesseract 5.x** and its language data must be installed on the host or container image, and the JVM must run with `--enable-native-access=ALL-UNNAMED` (already set in the Spring Boot run plugin and in Surefire/Failsafe via `pom.xml`).

**Install Tesseract on Debian/Ubuntu:**
```bash
sudo apt-get install -y tesseract-ocr
# For additional languages, install the matching language pack:
sudo apt-get install -y tesseract-ocr-nld   # Dutch
sudo apt-get install -y tesseract-ocr-deu   # German
```

**Install Tesseract on RHEL/Fedora:**
```bash
sudo dnf install -y tesseract
sudo dnf install -y tesseract-langpack-nld  # Dutch
```

**Docker example:**
```dockerfile
RUN apt-get update && apt-get install -y tesseract-ocr tesseract-ocr-eng && rm -rf /var/lib/apt/lists/*
```

If Tesseract is not installed, PDF files with an embedded text layer still work fine; OCR-dependent tests are skipped automatically.

### Extraction configuration

```bash
# Set the languages (ISO 639-1 two-letter or Tesseract three-letter codes):
export JSMD_EXTRACT_OCR_LANGUAGES=en,nl

# Point to a specific tessdata directory (optional):
export JSMD_EXTRACT_TESSDATA_PATH=/usr/share/tesseract-ocr/5/tessdata

# Override the library name if the soname differs (e.g. on macOS):
export JSMD_EXTRACT_TESSERACT_LIBRARY=libtesseract.5.dylib

# Increase DPI for higher OCR accuracy (at the cost of speed and memory):
export JSMD_EXTRACT_RENDER_DPI=400
```

## Documents

The **Documents** menu entry lists every known file path (50 per page, ordered by path) with its size and extraction state: *Text layer*, *OCR*, *No text*, or *Pending* when no text has been stored yet. Identical content at several paths appears once per path. Clicking a file name opens its detail page with the extracted plaintext; the download icon beside the name downloads the original file.

Downloads are addressed by document id only and are verified before any bytes are sent. The recorded path must still be a readable regular file (symbolic links are refused) and its SHA-256 must match the hash recorded when it was indexed. Otherwise the page explains why: HTTP 404 if the file is gone, HTTP 409 if it has changed since it was indexed.

### Searching

The search box above the list finds files by the words in their extracted text (file names and paths are not searched). Results are ordered by relevance, best match first (ties by path), 50 per page, and show the file name and download icon, path, size and an excerpt with the matched words highlighted. The query is part of the URL (`/documents?q=...`), so a search can be bookmarked and paging keeps it. An empty search shows the normal list.

Query syntax (PostgreSQL `websearch_to_tsquery`; no input can cause a syntax error, and a query with no searchable words simply matches nothing):

| Query | Matches |
|-------|---------|
| `invoice` | files containing the word, in any case |
| `red bicycle` | files containing both words |
| `"red bicycle"` | files containing the words next to each other, in that order |
| `cat or dog` | files containing either word |
| `cat -fish` | files containing `cat` but not `fish` |

`or` and `-` apply to the term next to them, so `cat or dog -fish` means `cat`, or `dog` without `fish`.

Matching uses the PostgreSQL `simple` text search configuration: words are compared case-insensitively but without stemming, so `invoices` does not match `invoice` (use `invoice or invoices`). This keeps English and Dutch text, which OCR can produce side by side, searchable alike. Only the first 500,000 characters of a document's text are indexed, because PostgreSQL cannot build a larger search vector; words beyond that are not found, although the detail page shows the full text. The search index is maintained by the database itself, so newly extracted or re-extracted text is searchable immediately and no separate indexing step exists.

## Queue page

The **Queue** menu entry shows the work queues: per queue the number of items per status, and a list of up to 200 items with queue, status, attempts, last error, creation time and one known file path. Failed items are listed first, then claimed, pending and finished ones; a notice appears when the list is truncated (the counts always cover everything). The counts and list update themselves every 5 seconds without reloading the page (paused while the tab is hidden).

Permanently failed items (status `FAILED`) can be removed one by one with **Remove**. **Purge finished items** (always shown, after a confirmation) removes all failed, done and cancelled items at once; pending and claimed items are never purged by hand. Separately, a daily cleanup deletes every item older than `JSMD_QUEUE_RETENTION` whatever its status (see Configuration). The underlying content is kept and is not queued again until it changes, so a purged item stays unprocessed.

Every read made for the page runs in a read-only transaction at `READ UNCOMMITTED` and takes no row locks, so watching the queue cannot delay claiming, completing or failing items. PostgreSQL does not implement `READ UNCOMMITTED` and treats it as `READ COMMITTED`: you never see uncommitted changes of other transactions, but reads still do not wait for them, because PostgreSQL readers and writers do not block each other. Removing and purging are ordinary writes.

## Run locally

With Docker running and JDK 27 selected, this is all you need:

```bash
export JAVA_HOME=/usr/lib/jvm/jre-27-openjdk
mvn spring-boot:run
```

`spring-boot:run` activates the `dev` Spring profile, which:

- starts the PostgreSQL service from `docker-compose.yml` if it is not already running (Spring Boot Docker Compose support, `start-only`: the container and its data survive restarts of the application) and connects to it; running `docker compose up -d --wait` yourself first also works;
- supplies a **development admin account, `admin` / `admin`**, unless you set `JSMD_ADMIN_USERNAME`/`JSMD_ADMIN_PASSWORD`, which then win. A warning is logged while the dev account is in use.

- scans the git-ignored `dev-documents/` directory in the project root (created at startup if missing) every minute, and polls the extraction queue every minute. Drop PDFs or images in there and they appear under Documents within about two minutes. Override with `JSMD_SCAN_DIRECTORIES`, `JSMD_SCAN_INTERVAL` and `JSMD_EXTRACT_POLL_INTERVAL`.

Open <http://localhost:8080> and sign in. Flyway applies the database migrations automatically at startup, and each migration is applied only once.

The `dev` profile is never active for `java -jar`, tests or any packaged deployment: there `JSMD_ADMIN_USERNAME` and `JSMD_ADMIN_PASSWORD` are required, no containers are started, and the database is the one given by `JSMD_DB_*`. While Compose support is active, it supplies the database connection and `JSMD_DB_*` is ignored; port 5432 must be free.

Stop PostgreSQL with `docker compose down` (add `-v` to also delete its data).

## Security notes

- Login credentials are sent in a form POST body. **Serve the application over TLS** (for example behind a reverse proxy) in any real deployment; plain HTTP is acceptable for local use only.
- Sessions are held in memory, so restarting the application signs everyone out.
- Only `/login`, `/css/**` and `/actuator/health` are accessible without signing in. The health endpoint reports only the overall status.

## Build and test

```bash
export JAVA_HOME=/usr/lib/jvm/jre-27-openjdk
mvn verify
```

Unit and MVC tests run with Surefire; the `*IT` integration tests run with Failsafe and need Docker, as they start PostgreSQL through Testcontainers.
