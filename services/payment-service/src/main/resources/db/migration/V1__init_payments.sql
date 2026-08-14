CREATE TABLE payment_authorizations (
    id                UUID PRIMARY KEY,
    order_id          UUID NOT NULL,
    amount            NUMERIC(19, 2) NOT NULL,
    currency          VARCHAR(3) NOT NULL,
    status            VARCHAR(16) NOT NULL,
    gateway_reference VARCHAR(128),
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL
);

-- One authorization attempt per order, ever — makes the authorize command
-- idempotent at the data layer, same pattern as inventory's reservations.
CREATE UNIQUE INDEX uq_payment_authorizations_order_id ON payment_authorizations (order_id);
