package com.keystone.orders.domain;

/**
 * PAYMENT_AUTHORIZED is deliberately not a state here: in this saga, payment
 * authorization and order confirmation happen in the same handler with
 * nothing gating between them, so persisting an interim state for it would
 * never actually be observable — it'd never survive to a commit on its own.
 */
public enum OrderStatus {
    PENDING,
    INVENTORY_RESERVED,
    CONFIRMED,
    CANCELLED
}
