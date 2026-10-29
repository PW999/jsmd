-- File index and work queues.
-- content: one row per distinct SHA-256; file_location: every known path; work_item: process/delete queues.

CREATE TABLE content (
    id         bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sha256     bytea       NOT NULL UNIQUE,
    size       bigint      NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE SEQUENCE scan_id_seq;

CREATE TABLE file_location (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    path              text   NOT NULL UNIQUE,
    content_id        bigint NOT NULL REFERENCES content (id),
    size              bigint NOT NULL,
    modified_at_ms    bigint NOT NULL,
    last_seen_scan_id bigint NOT NULL
);

CREATE INDEX file_location_content_id_idx ON file_location (content_id);

CREATE TABLE work_item (
    id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    queue        text        NOT NULL CHECK (queue IN ('PROCESS', 'DELETE')),
    content_id   bigint      NOT NULL REFERENCES content (id),
    status       text        NOT NULL CHECK (status IN ('PENDING', 'CLAIMED', 'DONE', 'FAILED', 'CANCELLED')),
    attempts     integer     NOT NULL DEFAULT 0,
    available_at timestamptz NOT NULL DEFAULT now(),
    claimed_at   timestamptz,
    last_error   text,
    created_at   timestamptz NOT NULL DEFAULT now()
);

-- At most one open (pending or in-progress) item per content per queue.
CREATE UNIQUE INDEX work_item_open_uq ON work_item (queue, content_id) WHERE status IN ('PENDING', 'CLAIMED');

CREATE INDEX work_item_claim_idx ON work_item (queue, available_at, id) WHERE status = 'PENDING';
