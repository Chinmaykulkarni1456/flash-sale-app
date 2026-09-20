CREATE TABLE reservations (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    sku_id VARCHAR(64) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0), -- FAIL-SAFE: Prevent zero or negative reservation requests
    status VARCHAR(32) NOT NULL, -- ACTIVE, CONFIRMED, CANCELLED, EXPIRED
    idempotency_key VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    -- CONCURRENCY & INTEGRITY: Hard DB constraint preventing duplicate allocations for same idempotency key per tenant
    CONSTRAINT uk_tenant_idempotency UNIQUE (tenant_id, idempotency_key)
);

-- PERFORMANCE: Index to optimize batch lookups for the background TTL expiry worker
CREATE INDEX idx_active_expired_reservations ON reservations (status, expires_at) WHERE status = 'ACTIVE';

CREATE TABLE domain_events (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);