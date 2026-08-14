-- Demo catalog seed data. sku-limited-edition is deliberately scarce so the
-- happy-path and insufficient-stock paths can both be demonstrated without
-- any manual setup.
INSERT INTO inventory_items (product_id, available_quantity, reserved_quantity, version) VALUES
    ('sku-widget', 500, 0, 0),
    ('sku-gadget', 50, 0, 0),
    ('sku-limited-edition', 2, 0, 0);
