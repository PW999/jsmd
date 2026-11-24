# Tasks

## 1. Dependencies and database setup

- [x] 1.1 Add the LangChain4j Spring Boot 4 starters (core plus Ollama) and the pgvector JDBC driver to `pom.xml`, then verify dependency resolution succeeds
- [x] 1.2 Switch the `docker-compose.yml` Postgres image from `postgres:17-alpine` to `pgvector/pgvector:17`, then verify the container exposes the `vector` extension
- [x] 1.3 Add Flyway migration that adds the `content_text.embedding [PERSON_NAME])` column plus its index, then verify the migration applies cleanly

## 2. Embedding storage in extraction

- [x] 2.1 Update `ContentTextRepository.upsert` to persist the embedding together with the text, then verify against the content text storage tests
- [x] 2.2 Wire embedding into the `PROCESS` claim flow before the text upsert, then verify that an embedding failure marks the queue item failed

## 3. Embedding configuration and generation

- [x] 3.1 Add `jsmd.embed` configuration properties (enabled, base-uri, model, dimension, timeout) and bind them, then verify the application binds them
- [x] 3.2 Implement the Ollama embedding client using the LangChain4j `EmbeddingModel`, then verify that a known text yields a vector of the configured dimension
- [x] 3.3 Generate embeddings for extracted text when `jsmd.embed.enabled` is true, and store text without vectors when false, then verify both paths

## 4. Vectorized search on the Documents page

- [x] 4.1 Add the vector search query with pgvector cosine distance to `DocumentRepository`, then verify with integration tests
- [x] 4.2 Add the `mode` search parameter to the `DocumentsController`, the vector search path, and the search template, then verify the mode control, results and pager behaviour

## 5. Verification

- [x] 5.1 Run the full integration test suite against the pgvector container and resolve failures
- [x] 5.2 Update the README with the new search mode, embedding configuration and database image

## Workflow follow-up

- Run the `/opsx-apply` workflow when the user is ready to implement these tasks.
