-- ============================================
-- V7: Add Kafka Metadata to Dead Letter Queue
-- ============================================
-- Adds Kafka-specific columns to DLQ for better
-- replay capability and debugging.
--
-- New columns:
--   - original_topic: Source Kafka topic
--   - partition_id: Kafka partition
--   - offset_id: Kafka offset
--   - message_key: Kafka message key (store_id)
--
-- Also updates error_type constraint to match
-- entity enum values.
--
-- ============================================

-- Add Kafka metadata columns
ALTER TABLE dead_letter_queue
    ADD COLUMN original_topic VARCHAR(255),
    ADD COLUMN partition_id INTEGER,
    ADD COLUMN offset_id BIGINT,
    ADD COLUMN message_key VARCHAR(255);

-- Remove old error_type constraint
ALTER TABLE dead_letter_queue
    DROP CONSTRAINT IF EXISTS chk_dlq_error_type;

-- Add new error_type constraint matching entity enum
ALTER TABLE dead_letter_queue
    ADD CONSTRAINT chk_dlq_error_type CHECK (
        error_type IN (
            'VALIDATION_ERROR',      -- Invalid order data
            'DATA_INTEGRITY',        -- Database constraint violation
            'UNKNOWN'                -- Unexpected error
        )
    );

-- Update existing rows (if any) to have default values
UPDATE dead_letter_queue
SET original_topic = 'orders.incoming',
    partition_id = 0,
    offset_id = 0
WHERE original_topic IS NULL;

-- Make columns non-nullable after setting defaults
ALTER TABLE dead_letter_queue
    ALTER COLUMN original_topic SET NOT NULL,
    ALTER COLUMN partition_id SET NOT NULL,
    ALTER COLUMN offset_id SET NOT NULL;

-- Update column comments
COMMENT ON COLUMN dead_letter_queue.original_topic IS 'Source Kafka topic for replay';
COMMENT ON COLUMN dead_letter_queue.partition_id IS 'Kafka partition for correlation';
COMMENT ON COLUMN dead_letter_queue.offset_id IS 'Kafka offset for correlation';
COMMENT ON COLUMN dead_letter_queue.message_key IS 'Kafka message key (store_id)';
