package com.yotpo.orders.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Dead letter queue entry for orders that permanently failed processing.
 *
 * Purpose:
 * - Store permanently failed messages for manual review
 * - Preserve complete context for debugging
 * - Track error patterns by classification
 *
 * Error Classification:
 * - VALIDATION_ERROR: Invalid order data (permanent)
 * - DATA_INTEGRITY: Constraint violations (permanent)
 * - UNKNOWN: Unexpected errors (needs investigation)
 *
 * Operations Review:
 * - Monitor DLQ size in metrics
 * - Investigate root causes
 * - Fix and replay if possible
 */
@Entity
@Table(name = "dead_letter_queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeadLetterQueueEntry {

    /**
     * Primary key - auto-generated UUID.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Order ID from the failed message.
     */
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    /**
     * Original Kafka topic the message came from.
     */
    @Column(name = "original_topic", nullable = false, length = 255)
    private String originalTopic;

    /**
     * Kafka partition (for replay correlation).
     */
    @Column(name = "partition_id", nullable = false)
    private Integer partitionId;

    /**
     * Kafka offset (for replay correlation).
     */
    @Column(name = "offset_id", nullable = false)
    private Long offsetId;

    /**
     * Kafka message key (store_id for orders).
     */
    @Column(name = "message_key", length = 255)
    private String messageKey;

    /**
     * Original message payload (preserved for replay).
     * Stored as JSONB in PostgreSQL.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "message_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> messagePayload;

    /**
     * Error classification for categorization.
     * Helps prioritize investigation and identify patterns.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "error_type", nullable = false, length = 50)
    private ErrorType errorType;

    /**
     * Detailed error message.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Full stack trace (for debugging).
     */
    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    /**
     * Number of processing attempts before DLQ.
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * When this entry was created.
     */
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * Set creation timestamp before persisting.
     */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    /**
     * Error type classification for DLQ entries.
     */
    public enum ErrorType {
        /**
         * Invalid order data - permanent failure.
         * Examples: Missing required fields, invalid format
         */
        VALIDATION_ERROR,

        /**
         * Database constraint violation - permanent failure.
         * Examples: Duplicate key, foreign key violation
         */
        DATA_INTEGRITY,

        /**
         * Unexpected/unknown error - needs investigation.
         * Examples: NullPointerException, unexpected state
         */
        UNKNOWN
    }
}
