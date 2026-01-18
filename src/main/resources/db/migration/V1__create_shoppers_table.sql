-- ============================================
-- V1: Create Shoppers Table
-- ============================================
-- Shoppers are NORMALIZED as first-class entities.
-- Yotpo needs to track shoppers across orders for review requests.
--
-- Business Rules:
--   - (store_id, email) uniquely identifies a shopper within a store
--   - Same email in different stores = different shoppers
--   - Names can be updated (merchant might correct typos)
--
-- ============================================

CREATE TABLE shoppers (
    -- Primary key: Auto-generated sequential ID
    id BIGSERIAL PRIMARY KEY,

    -- Store identifier (merchant's store)
    -- Not a foreign key - stores are managed externally
    store_id VARCHAR(255) NOT NULL,

    -- Shopper email - unique identifier within a store
    email VARCHAR(255) NOT NULL,

    -- Shopper name (can be updated across orders)
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,

    -- Audit timestamps
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Unique constraint: One shopper per email per store
    CONSTRAINT uq_shoppers_store_email UNIQUE (store_id, email)
);

-- Add comment for documentation
COMMENT ON TABLE shoppers IS 'Shoppers who have placed orders. Normalized to track across multiple orders.';
COMMENT ON COLUMN shoppers.store_id IS 'Merchant store identifier';
COMMENT ON COLUMN shoppers.email IS 'Shopper email - unique within a store';
