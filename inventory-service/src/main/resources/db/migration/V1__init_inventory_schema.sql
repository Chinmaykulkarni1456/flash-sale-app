CREATE TABLE products (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_sku UNIQUE (tenant_id, sku)
);

CREATE TABLE warehouse_stock (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(64) NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    warehouse_id VARCHAR(64) NOT NULL,
    on_hand INT NOT NULL DEFAULT 0,
    reserved INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_product_warehouse UNIQUE (tenant_id, product_id, warehouse_id),
    CONSTRAINT chk_on_hand_non_negative CHECK (on_hand >= 0),
    CONSTRAINT chk_reserved_non_negative CHECK (reserved >= 0),
    CONSTRAINT chk_reserved_within_on_hand CHECK (reserved <= on_hand)
);

CREATE TABLE inventory_domain_events (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_products_tenant_sku ON products(tenant_id, sku);
CREATE INDEX idx_stock_tenant_product ON warehouse_stock(tenant_id, product_id);
CREATE INDEX idx_events_tenant_created ON inventory_domain_events(tenant_id, created_at);