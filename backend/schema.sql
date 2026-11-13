-- Haraya Dock Supabase PostgreSQL Schema
-- Tables: orders, licenses with indexes and Row Level Security (RLS)

-- 1. Orders Table
CREATE TABLE IF NOT EXISTS orders (
    order_id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL,
    email TEXT,
    phone TEXT,
    amount INTEGER NOT NULL DEFAULT 150000, -- Amount in IDR (or 1000 for $10 USD)
    currency TEXT NOT NULL DEFAULT 'IDR',
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PAID', 'EXPIRED', 'FAILED')),
    payment_url TEXT,
    license_key TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    paid_at TIMESTAMPTZ
);

-- Fast lookup indexes
CREATE INDEX IF NOT EXISTS idx_orders_device_id ON orders(device_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at DESC);

-- 2. Licenses Table (1 device = 1 active license)
CREATE TABLE IF NOT EXISTS licenses (
    device_id TEXT PRIMARY KEY,
    license_key TEXT NOT NULL,
    customer_email TEXT,
    customer_phone TEXT,
    order_id TEXT REFERENCES orders(order_id) ON DELETE SET NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_licenses_customer_email ON licenses(customer_email);
CREATE INDEX IF NOT EXISTS idx_licenses_order_id ON licenses(order_id);
CREATE INDEX IF NOT EXISTS idx_licenses_status ON licenses(status);

-- Automatic trigger for updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_licenses_updated_at ON licenses;
CREATE TRIGGER trigger_licenses_updated_at
BEFORE UPDATE ON licenses
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- 3. Row Level Security (RLS)
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE licenses ENABLE ROW LEVEL SECURITY;

-- Note: The Cloudflare Worker accesses Supabase using the service_role key,
-- which bypasses RLS automatically in PostgreSQL.
-- Defense-in-depth: No public anon read/write policies are granted.

