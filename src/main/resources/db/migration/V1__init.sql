-- ============================================================
-- V1__init.sql — Initial schema for Order Payment System
-- Managed by Flyway; Hibernate ddl-auto=validate
-- ============================================================

-- ENUM types
CREATE TYPE order_status AS ENUM (
    'PENDING',
    'PAYMENT_PROCESSING',
    'PAYMENT_SUCCESS',
    'PAYMENT_FAILED',
    'CANCELLED'
);

CREATE TYPE payment_status AS ENUM (
    'INITIATED',
    'SUCCESS',
    'FAILED'
);

-- -------------------------------------------------------
-- orders table
-- -------------------------------------------------------
CREATE TABLE orders (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id   VARCHAR(64)   NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    product_name  VARCHAR(255)  NOT NULL,
    quantity      INT           NOT NULL CHECK (quantity > 0),
    amount        NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    status        order_status  NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(255) UNIQUE,          -- prevents duplicate orders from clients
    created_at    TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_customer_id  ON orders(customer_id);
CREATE INDEX idx_orders_status       ON orders(status);
CREATE INDEX idx_orders_created_at   ON orders(created_at);

-- -------------------------------------------------------
-- payments table
-- -------------------------------------------------------
CREATE TABLE payments (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id      UUID          NOT NULL REFERENCES orders(id),
    amount        NUMERIC(15,2) NOT NULL,
    status        payment_status NOT NULL DEFAULT 'INITIATED',
    failure_reason VARCHAR(512),
    transaction_id VARCHAR(128),                  -- external payment gateway reference
    processed_at  TIMESTAMP,
    created_at    TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_order_id   ON payments(order_id);
CREATE INDEX idx_payments_status     ON payments(status);

-- -------------------------------------------------------
-- processed_events — Idempotency guard for Kafka consumers
-- Stores eventId + topic + timestamp to detect redeliveries
-- -------------------------------------------------------
CREATE TABLE processed_events (
    event_id      VARCHAR(255)  NOT NULL,
    topic         VARCHAR(255)  NOT NULL,
    processed_at  TIMESTAMP     NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, topic)
);

CREATE INDEX idx_processed_events_processed_at ON processed_events(processed_at);

-- -------------------------------------------------------
-- Trigger: auto-update updated_at on row change
-- -------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_payments_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
