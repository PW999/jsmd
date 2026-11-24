# Design

## Context

The system currently stores extracted text in `content_text` and maintains a generated `tsvector` column (`search_vector`) with a GIN index, implementing full-text search through `websearch_to_tsquery` and `ts_rank_cd` in `DocumentRepository`. Extraction flows through the `PROCESS` work queue: `ExtractionService` claims an item, extracts text via the PDF/OCR extractors, and persists through `ContentTextRepository.upsert`. There is no embedding infrastructure yet.

The proposal (proposal.md) adds semantic search via LangChain4j (Spring Boot 4 starter + Ollama embedding starter), a `vector` column on `content_text`, and a mode toggle on the Documents search page. This design records the technical decisions needed to build it.

## Goals / Non-Goals

**Goals:**
- Embed extracted text before it is stored, through the configured Ollama embedding model, and store the vector alongside the text.
- Run the vector similarity search in PostgreSQL via pgvector's cosine distance operator.

**Non-Goals:**
- Hybrid (BM25 + vector) scoring; this change ships the two modes separately, with full-text remaining the default.
- Query rewriting (spelling correction, expansion); queries are embedded as-is.

## Decisions

### D1: LangChain4j Ollama embedding model
Use `dev.langchain4j:langchain4j-ollama-spring-boot4-starter` (Spring Boot 4 variant) with the `dev.langchain4j:langchain4j-spring-boot4-starter` parent stack so the `EmbeddingModel` bean (Ollama) is available on the `ApplicationContext`. The model is configured through `jsmd.embed.*` properties: base URI (default `http://localhost:11434`), model name (default `nomic-embed-text:v1.5`), dimension (default `768`), and enabled flag (default `true`).

### D2: Vector storage on `content_text`
Add an `embedding vector(768) NULL` column to `content_text` via Flyway migration (V5), so text and vector are stored in one row and upsert semantics replace both together. No separate embedding table is added.

### D3: Vector similarity search
Embed the query with the same `EmbeddingModel`, then run:

```sql
SELECT fl.id, fl.path, fl.size, ct.embedding
FROM file_location fl
JOIN content_text ct ON ct.content_id = fl.content_id
WHERE ct.embedding IS NOT NULL
ORDER BY ct.embedding <=> ?::vector
LIMIT ? OFFSET ?
```

using the pgvector cosine distance operator. Bind the query vector as a float array via the pgvector JDBC driver. Results are ranked best match first; ties broken by path then id.

### D4: Extraction-time embedding with fail-and-retry
Embedding runs inside the `PROCESS` item processing, after extraction and before/with the text upsert. When embedding fails, the item is marked failed and retried per the existing queue rules; text is not stored without a vector (per the `text-extraction` delta).

### D5: Search mode via `mode` parameter
The Documents page reads `q` and `mode` (`fts` default, `vector` opt-in) as request parameters. The mode control is rendered as radio buttons next to the search field; paging links preserve both `q` and `mode`.

### D6: Vector mode result content
In vector mode results are title/path/size only; no text snippet is rendered because term-based highlighting does not apply (per the answered ambiguity).

## Risks / Trade-offs

- [Risk] Ollama must be running for extraction and for vector search.
  → Mitigation: `jsmd.embed.enabled` defaults to true; when disabled, extraction stores no vectors and vector search returns no results.
- [Risk] pgvector extension must be available on the Postgres server.
  → Mitigation: Docker Compose moves to `pgvector/pgvector:17`; tests move to the pgvector test image.
- [Risk] Changing the embedding model invalidates existing vector dimensions.
  → Mitigation: dimension is pinned via `jsmd.embed.dimension` (default `768`) and changing models requires a deliberate migration.

## Migration Plan

1. Add Flyway migration V5 (`ALTER TABLE content_text ADD COLUMN embedding vector(768)` + ivfflat index `content_text_embedding_idx`).
2. Switch `docker-compose.yml` Postgres image to `pgvector/pgvector:17`.
3. Add `dev.langchain4j` starter dependencies and `com.pgvector:pgvector` driver; add `jsmd.embed.*` properties.
4. Wire embedding into `ExtractionService` → `ContentTextRepository.upsert`; enable embedding failures to fail the item.
5. Add `mode` handling to `DocumentsController`, `DocumentRepository.searchVector`, and the `documents` template; add vector query embedding path.
6. Validate with integration tests (pgvector container) and run the suite.

Rollback: revert application code and configuration; the additive column/index can be left in place (nullable, unused) without effect.

## Open Questions

- None that affect the specs, approach, or task breakdown at this time; a follow-up may revisit hybrid scoring or a backfill for pre-existing stored text.
