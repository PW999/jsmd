-- Full text search over extracted text.
-- The 'simple' configuration matches words case-insensitively without stemming, which is
-- correct for mixed English/Dutch text. Only the first 500,000 characters are indexed because
-- PostgreSQL rejects a tsvector larger than 1 MB, which would make storing text fail.
-- A generated column keeps the vector current for every insert and update.

ALTER TABLE content_text
    ADD COLUMN search_vector tsvector
        GENERATED ALWAYS AS (to_tsvector('simple', left(text, 500000))) STORED;

CREATE INDEX content_text_search_idx ON content_text USING gin (search_vector);
