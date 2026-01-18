-- ============================================
-- V6: Create Dead Letter Queue Table
-- ============================================
-- Stores messages that failed consumer processing.
--
-- Purpose:
--   - Fault isolation: Prevent poison pills from blocking partition
--   - Operational visibility: DLQ alerts for manual intervention
--   - Debugging: Full error context for investigation
--
-- Error Types:
--   - PERMANENT_ERROR: Data integrity violation (won't succeed on retry)
--   - MAX_RETRIES_EXCEEDED: Transient error persisted after 10 retries
--   - UNEXPECTED_ERROR: Unknown error requiring investigation
--
-- Lifecycle:
--   1. Consumer processes message from Kafka
--   2. On permanent/max-retry error, message sent to DLQ
--   3. Kafka offset is committed (unblocks partition)
--   4. Operations team investigates and resolves
--   5. Optional: Replay message after fix
--
-- ============================================

CREATE TABLE dead_letter_queue (
    -- Primary key: Auto-generated UUID
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Order ID from the failed message
    order_id UUID NOT NULL,

    -- Complete Kafka message payload (JSON)
    -- Preserved for potential replay after fix
    message_payload JSONB NOT NULL,

    -- Error classification
    error_type VARCHAR(50) NOT NULL,

    -- Error details
    error_message TEXT NOT NULL,
    stack_trace TEXT,

    -- How many times consumer retried before DLQ
    retry_count INTEGER NOT NULL DEFAULT 0,

    -- When the message was sent to DLQ
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Error type constraint
    CONSTRAINT chk_dlq_error_type CHECK (
        error_type IN (
            'PERMANENT_ERROR',       -- Data integrity violation
            'MAX_RETRIES_EXCEEDED',  -- Transient error persisted
            'UNEXPECTED_ERROR'       -- Unknown error
        )
    ),

    -- Retry count constraint
    CONSTRAINT chk_dlq_retry_count
        CHECK (retry_count >= 0)
);

-- Index for monitoring dashboard (recent errors first)
CREATE INDEX idx_dlq_created
    ON dead_letter_queue(created_at DESC);

-- Index for error type filtering
CREATE INDEX idx_dlq_error_type
    ON dead_letter_queue(error_type, created_at DESC);

-- Index for order lookup
CREATE INDEX idx_dlq_order_id
    ON dead_letter_queue(order_id);

-- Comments
COMMENT ON TABLE dead_letter_queue IS 'Failed consumer messages - requires manual intervention';
COMMENT ON COLUMN dead_letter_queue.error_type IS 'PERMANENT_ERROR | MAX_RETRIES_EXCEEDED | UNEXPECTED_ERROR';
COMMENT ON COLUMN dead_letter_queue.message_payload IS 'Original message for potential replay';
COMMENT ON INDEX idx_dlq_created IS 'Dashboard: Recent errors first';
