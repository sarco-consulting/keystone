CREATE TABLE orders (
    id             UUID PRIMARY KEY,
    customer_id    VARCHAR(64) NOT NULL,
    status         VARCHAR(32) NOT NULL,
    currency       VARCHAR(3)  NOT NULL,
    total_amount   NUMERIC(19, 2) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE order_line_items (
    id             UUID PRIMARY KEY,
    order_id       UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id     VARCHAR(64) NOT NULL,
    quantity       INTEGER NOT NULL CHECK (quantity > 0),
    unit_price     NUMERIC(19, 2) NOT NULL CHECK (unit_price >= 0)
);

CREATE INDEX idx_order_line_items_order_id ON order_line_items (order_id);
