CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN', 'STAFF')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (username = lower(btrim(username))),
    CHECK (length(username) > 0)
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    unit_price NUMERIC(12,2) NOT NULL
        CHECK (unit_price BETWEEN 0 AND 999999.99),
    reorder_threshold BIGINT NOT NULL DEFAULT 0
        CHECK (reorder_threshold >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    on_hand BIGINT NOT NULL DEFAULT 0 CHECK (on_hand >= 0),
    reserved BIGINT NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (sku ~ '^[A-Z0-9][A-Z0-9._-]{0,63}$'),
    CHECK (length(btrim(name)) > 0),
    CHECK (reserved <= on_hand)
);

CREATE TABLE customers (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL CHECK (length(btrim(name)) > 0),
    email VARCHAR(254),
    phone VARCHAR(40),
    notes VARCHAR(2000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    customer_name_snapshot VARCHAR(200) NOT NULL,
    customer_email_snapshot VARCHAR(254),
    customer_phone_snapshot VARCHAR(40),
    status VARCHAR(16) NOT NULL
        CHECK (status IN ('CONFIRMED', 'FULFILLED', 'CANCELLED')),
    currency CHAR(3) NOT NULL DEFAULT 'EUR' CHECK (currency = 'EUR'),
    total NUMERIC(18,2) NOT NULL CHECK (total >= 0),
    notes VARCHAR(2000),
    created_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fulfilled_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(500),
    CHECK (
        (status = 'CONFIRMED'
         AND fulfilled_at IS NULL AND cancelled_at IS NULL
         AND cancellation_reason IS NULL)
        OR
        (status = 'FULFILLED'
         AND fulfilled_at IS NOT NULL AND cancelled_at IS NULL
         AND cancellation_reason IS NULL)
        OR
        (status = 'CANCELLED'
         AND fulfilled_at IS NULL AND cancelled_at IS NOT NULL
         AND cancellation_reason IS NOT NULL
         AND length(btrim(cancellation_reason)) > 0)
    )
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    sku_snapshot VARCHAR(64) NOT NULL,
    name_snapshot VARCHAR(200) NOT NULL,
    unit_price_snapshot NUMERIC(12,2) NOT NULL
        CHECK (unit_price_snapshot BETWEEN 0 AND 999999.99),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 1000000),
    line_total NUMERIC(18,2) NOT NULL CHECK (line_total >= 0),
    UNIQUE (order_id, product_id),
    CHECK (line_total = unit_price_snapshot * quantity)
);

CREATE TABLE stock_movements (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    order_id UUID REFERENCES orders(id),
    actor_id UUID NOT NULL REFERENCES app_users(id),
    type VARCHAR(16) NOT NULL
        CHECK (type IN ('RECEIPT', 'ADJUSTMENT', 'RESERVE', 'FULFIL', 'RELEASE')),
    delta_on_hand BIGINT NOT NULL,
    delta_reserved BIGINT NOT NULL,
    on_hand_after BIGINT NOT NULL CHECK (on_hand_after >= 0),
    reserved_after BIGINT NOT NULL CHECK (reserved_after >= 0),
    reason VARCHAR(500) NOT NULL CHECK (length(btrim(reason)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (reserved_after <= on_hand_after),
    CHECK (
        (type = 'RECEIPT' AND order_id IS NULL
         AND delta_on_hand > 0 AND delta_reserved = 0)
        OR
        (type = 'ADJUSTMENT' AND order_id IS NULL
         AND delta_on_hand <> 0 AND delta_reserved = 0)
        OR
        (type = 'RESERVE' AND order_id IS NOT NULL
         AND delta_on_hand = 0 AND delta_reserved > 0)
        OR
        (type = 'FULFIL' AND order_id IS NOT NULL
         AND delta_on_hand < 0 AND delta_reserved = delta_on_hand)
        OR
        (type = 'RELEASE' AND order_id IS NOT NULL
         AND delta_on_hand = 0 AND delta_reserved < 0)
    )
);

CREATE TABLE order_events (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    actor_id UUID NOT NULL REFERENCES app_users(id),
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (
        (from_status IS NULL AND to_status = 'CONFIRMED')
        OR
        (from_status IS NOT NULL AND from_status = 'CONFIRMED'
         AND to_status IN ('FULFILLED', 'CANCELLED'))
    ),
    UNIQUE (order_id, to_status)
);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES app_users(id),
    operation VARCHAR(40) NOT NULL
        CHECK (operation IN ('CREATE_ORDER', 'RECEIVE_STOCK', 'ADJUST_STOCK')),
    idempotency_key UUID NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_status INTEGER,
    response_body JSONB,
    response_location VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    UNIQUE (actor_id, operation, idempotency_key),
    CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CHECK (
        (response_status IS NULL AND response_body IS NULL AND completed_at IS NULL)
        OR
        (response_status IS NOT NULL AND response_status BETWEEN 200 AND 299
         AND response_body IS NOT NULL AND completed_at IS NOT NULL)
    )
);