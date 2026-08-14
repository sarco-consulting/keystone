package com.keystone.events;

/** Single source of truth for topic names — no magic strings duplicated across services. */
public final class Topics {

    private Topics() {
    }

    public static final String ORDER_EVENTS = "order.events";
    public static final String INVENTORY_COMMANDS = "inventory.commands";
    public static final String INVENTORY_EVENTS = "inventory.events";
    public static final String PAYMENT_COMMANDS = "payment.commands";
    public static final String PAYMENT_EVENTS = "payment.events";
}
