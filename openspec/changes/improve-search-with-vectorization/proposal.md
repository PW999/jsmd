# Proposal

## Why

Current search is keyword-based (PostgreSQL `websearch_to_tsquery`), which misses semantically related wording and struggles with synonyms, paraphrasing, and misspellings. Adding vector embeddings (via LangChain4j + Ollama, defaulting to `nomic-embed-text:v1.5`) lets the search screen offer a semantic mode alongside the existing full-text search, so users can find documents by meaning. The database must switch to `pgvector/pgvector` to store the vectors.

## What Changes

- Add the `langchain4j-spring-boot4-starter` and `langchain4j-ollama-spring-boot4-starter` dependencies to the Spring Boot 4 project.
- Switch the Postgres database image to `pgvector/pgvector` (in `docker-compose.yml`) so the `vector` column type is available.
- Add a `vector` column to `content_text` (Flyway migration) and a GIN index over it for similarity search.
- Before a document's text is stored in the database, generate its embedding through the Ollama embedding model and store the vector alongside the text.
- Add a search-mode toggle (vectorized vs. plain full-text) to the Documents search screen, defaulting to full-text.
- Implement vectorized search using cosine similarity (` <=> `) with the same pagination, ranking (best match first), and access control as existing search.

## Capabilities

### New Capabilities

- `vectorized-search`: The system embeds extracted document text with an Ollama embedding model, stores the vector in PostgreSQL (pgvector), and exposes a similarity-based search mode on the Documents page alongside the existing full-text search.

### Modified Capabilities

- `document-search`: The search screen gains a mode toggle (vectorized vs. full-text) and the full-text requirement stays unchanged; vectorized search is an additional mode.
- `text-extraction`: Storing extracted text now also embeds it and stores the vector; if embedding fails the item fails and is retried per queue rules.

## Impact

- **Dependencies**: `dev.langchain4j:langchain4j-spring-boot4-starter`, `dev.langchain4j:langchain4j-ollama-spring-boot4-starter` (Spring Boot 4 starters).
- **Database**: Postgres image changes to `pgvector/pgvector`; new Flyway migration adds a `vector(768)` column and index on `content_text`.
- **Configuration**: New `jsmd.embed.*` properties (Ollama base URI, model name, enabled flag, batch/dimension settings).
- **Code**: New embedding service and repository methods in `com.pw999.jsmd.embed`; `ExtractionService` calls embedding before `ContentTextRepository.upsert`; `DocumentsController` and the `documents` template gain a mode parameter; `DocumentRepository` gains a vector search method.
- **Operations**: Ollama must be running with the `nomic-embed-text` model pulled; the app refuses to start (or disables extraction) if embedding is enabled but unreachable — see design.md.