CREATE TABLE outbox_messages (
    id            UUID PRIMARY KEY,
    saga_id       UUID NOT NULL,
    topic         VARCHAR(128) NOT NULL,
    message_key   VARCHAR(128) NOT NULL,
    message_type  VARCHAR(128) NOT NULL,
    payload       TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    published_at  TIMESTAMPTZ
);

CREATE INDEX idx_outbox_messages_unpublished ON outbox_messages (created_at) WHERE published_at IS NULL;
