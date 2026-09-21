CREATE TABLE orders (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    reservation_id UUID NOT NULL UNIQUE,
    sku_id VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    payment_transaction_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_tenant_order_status ON orders(tenant_id, status);
CREATE INDEX idx_created_at ON orders(created_at);

CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    order_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    transaction_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);