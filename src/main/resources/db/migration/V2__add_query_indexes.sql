CREATE INDEX idx_orders_status_created
    ON orders(status, created_at DESC, id);
CREATE INDEX idx_orders_customer_created
    ON orders(customer_id, created_at DESC, id);
CREATE INDEX idx_orders_created_by ON orders(created_by);
CREATE INDEX idx_order_items_product ON order_items(product_id);
CREATE INDEX idx_movements_product_created
    ON stock_movements(product_id, created_at DESC, id);
CREATE INDEX idx_movements_order ON stock_movements(order_id);
CREATE INDEX idx_movements_actor ON stock_movements(actor_id);
CREATE INDEX idx_order_events_order_created
    ON order_events(order_id, created_at, id);
CREATE INDEX idx_order_events_actor ON order_events(actor_id);
CREATE UNIQUE INDEX uq_order_one_terminal_event
    ON order_events(order_id)
    WHERE to_status IN ('FULFILLED', 'CANCELLED');