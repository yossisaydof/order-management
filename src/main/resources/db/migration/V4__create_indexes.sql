-- ============================================
-- V4: Create Indexes
-- ============================================
-- Strategic indexes to optimize query patterns.
--
-- Design Principles:
--   - Index what we query (GET endpoints)
--   - Conservative approach (write-heavy system)
--   - Composite indexes for common filter combinations
--
-- Query Patterns Supported:
--   1. GET /orders/{id} - Primary key lookup (automatic)
--   2. GET /orders?from=&to= - Date range within store
--   3. GET /orders?email= - Filter by shopper email
--   4. Consumer JOINs - Shopper lookup by email
--
-- ============================================

-- ==========================================
-- Shoppers Indexes
-- ==========================================

-- Index for shopper lookup by store + email (used during order processing)
-- Supports: Upsert shopper operation in consumer
-- Note: Unique constraint already creates an index, but explicit for clarity
CREATE INDEX idx_shoppers_store_email
    ON shoppers(store_id, email);

-- ==========================================
-- Orders Indexes
-- ==========================================

-- Index for filtering orders by store and date range
-- Supports: GET /{store_id}/orders?from=&to=
-- DESC order for recent-first pagination
CREATE INDEX idx_orders_store_date
    ON orders(store_id, order_date DESC);

-- Index for shopper_id foreign key (JOINs)
-- Supports: Fetching orders with shopper details
CREATE INDEX idx_orders_shopper_id
    ON orders(shopper_id);

-- Composite index for store + created_at (alternative sorting)
-- Supports: Pagination by creation time
CREATE INDEX idx_orders_store_created
    ON orders(store_id, created_at DESC);

-- ==========================================
-- Line Items Indexes
-- ==========================================

-- Index for order_id foreign key
-- Supports: Fetching line items for an order
-- Note: Cascade delete also benefits from this index
CREATE INDEX idx_line_items_order_id
    ON line_items(order_id);

-- ==========================================
-- Documentation
-- ==========================================

COMMENT ON INDEX idx_shoppers_store_email IS 'Shopper lookup during order processing';
COMMENT ON INDEX idx_orders_store_date IS 'Date range queries: GET /orders?from=&to=';
COMMENT ON INDEX idx_orders_shopper_id IS 'JOIN performance for shopper details';
COMMENT ON INDEX idx_orders_store_created IS 'Pagination by creation time';
COMMENT ON INDEX idx_line_items_order_id IS 'Line items lookup and cascade delete';
