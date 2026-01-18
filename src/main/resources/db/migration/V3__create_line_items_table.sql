-- ============================================
-- V3: Create Line Items Table
-- ============================================
-- Line items are DENORMALIZED (not linked to a products table).
--
-- Design Decisions:
--   - Products are NOT normalized because:
--     1. Orders are immutable historical records
--     2. Must preserve price/name at time of purchase
--     3. external_product_id is merchant's ID, not Yotpo's
--     4. No product catalog management in scope
--
--   - Example: If merchant changes product from "$799 iPhone 13"
--     to "$699 iPhone 13 Refurbished", old orders must still
--     show the original $799 price.
--
-- ============================================

CREATE TABLE line_items (
    -- Primary key: Auto-generated sequential ID
    id BIGSERIAL PRIMARY KEY,

    -- Foreign key to orders table
    -- ON DELETE CASCADE: Delete line items when order is deleted
    order_id UUID NOT NULL,

    -- Merchant's product identifier (external to Yotpo)
    -- This is metadata, not a foreign key to a products table
    external_product_id VARCHAR(255) NOT NULL,

    -- Product details at time of purchase (DENORMALIZED)
    -- These are snapshots - they never change after order creation
    product_name VARCHAR(500) NOT NULL,
    product_description TEXT,

    -- Price at time of purchase
    -- NUMERIC(10,2) allows up to 99,999,999.99
    product_price NUMERIC(10, 2) NOT NULL,

    -- Quantity purchased
    quantity INTEGER NOT NULL,

    -- Foreign key constraint with cascade delete
    CONSTRAINT fk_line_items_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id)
        ON DELETE CASCADE,

    -- Business constraints
    CONSTRAINT chk_line_items_price_positive
        CHECK (product_price >= 0),
    CONSTRAINT chk_line_items_quantity_positive
        CHECK (quantity > 0)
);

-- Add comments for documentation
COMMENT ON TABLE line_items IS 'Purchased products - DENORMALIZED snapshot at time of purchase';
COMMENT ON COLUMN line_items.external_product_id IS 'Merchant product ID (not a FK - external to Yotpo)';
COMMENT ON COLUMN line_items.product_price IS 'Price at time of purchase - immutable';
COMMENT ON COLUMN line_items.product_name IS 'Product name at time of purchase - immutable';
