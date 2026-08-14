CREATE TABLE saga_steps (
    id          UUID PRIMARY KEY,
    order_id    UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    step        VARCHAR(64) NOT NULL,
    detail      TEXT,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_saga_steps_order_id ON saga_steps (order_id, occurred_at);
