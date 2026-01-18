package com.yotpo.orders.domain.repository;

import com.yotpo.orders.domain.entity.RetryQueueEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for RetryQueueEntry entity.
 *
 * Used by RetryQueueProcessor scheduled job to:
 * 1. Find entries ready for retry
 * 2. Delete successfully processed entries
 * 3. Update retry count on failure
 */
@Repository
public interface RetryQueueRepository extends JpaRepository<RetryQueueEntry, UUID> {

    /**
     * Find entries ready for retry (next_retry_at <= now).
     * Ordered by next_retry_at to process oldest first.
     * Filters entries that haven't exceeded max retries.
     *
     * @param now current timestamp
     * @param maxRetries maximum retry attempts before giving up
     * @param pageable limit results for batch processing
     * @return list of entries ready for retry
     */
    @Query("SELECT r FROM RetryQueueEntry r " +
           "WHERE r.nextRetryAt <= :now " +
           "AND r.retryCount < :maxRetries " +
           "ORDER BY r.nextRetryAt ASC")
    List<RetryQueueEntry> findEntriesReadyForRetry(
        @Param("now") OffsetDateTime now,
        @Param("maxRetries") int maxRetries,
        Pageable pageable
    );

    /**
     * Find entries that have exceeded max retries.
     * These should be moved to DLQ for manual review.
     *
     * @param maxRetries the maximum retry threshold
     * @return list of entries exceeding max retries
     */
    @Query("SELECT r FROM RetryQueueEntry r WHERE r.retryCount >= :maxRetries")
    List<RetryQueueEntry> findEntriesExceedingMaxRetries(@Param("maxRetries") int maxRetries);

    /**
     * Delete entries by order ID.
     * Used when order is successfully processed.
     *
     * @param orderId the order UUID
     */
    void deleteByOrderId(UUID orderId);

    /**
     * Count entries in retry queue.
     * Useful for monitoring/metrics.
     *
     * @return total count of retry queue entries
     */
    long count();
}
