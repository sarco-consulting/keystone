CREATE TABLE inventory_items (
    product_id          VARCHAR(64) PRIMARY KEY,
    available_quantity  INTEGER NOT NULL CHECK (available_quantity >= 0),
    reserved_quantity   INTEGER NOT NULL CHECK (reserved_quantity >= 0),
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE reservations (
    id           UUID PRIMARY KEY,
    order_id     UUID NOT NULL,
    product_id   VARCHAR(64) NOT NULL REFERENCES inventory_items (product_id),
    quantity     INTEGER NOT NULL CHECK (quantity > 0),
    status       VARCHAR(16) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);

-- Enforces the reserve command's idempotency at the data layer, not just in
-- application logic: at most one reservation per (order, product) ever.
CREATE UNIQUE INDEX uq_reservations_order_product ON reservations (order_id, product_id);
