-- Extracted text per content item.
-- source: TEXT_LAYER (PDF embedded text), OCR (Tesseract), NONE (no text found).
-- Upsert makes reprocessing idempotent.

CREATE TABLE content_text (
    content_id   bigint      PRIMARY KEY REFERENCES content (id),
    text         text        NOT NULL,
    source       text        NOT NULL CHECK (source IN ('TEXT_LAYER', 'OCR', 'NONE')),
    page_count   integer,
    extracted_at timestamptz NOT NULL DEFAULT now()
);
