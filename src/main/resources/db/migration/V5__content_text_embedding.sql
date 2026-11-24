-- Vector embedding storage for semantic search (pgvector).
-- The column is nullable: text is only embeddable when jsmd.embed is enabled, and vector search
-- excludes rows without a vector.

CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE content_text
    ADD COLUMN embedding vector(768);

CREATE INDEX content_text_embedding_idx ON content_text USING ivfflat (embedding vector_cosine_ops);
