package com.yotpo.orders.domain.repository;

import com.yotpo.orders.domain.entity.DeadLetterQueueEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for DeadLetterQueueEntry entity.
 *
 * Used for:
 * 1. Storing permanently failed messages
 * 2. Querying DLQ entries for investigation
 * 3. Monitoring error patterns by type
 */
@Repository
public interface DeadLetterQueueRepository extends JpaRepository<DeadLetterQueueEntry, UUID> {

    /**
     * Find DLQ entries by error type.
     * Useful for investigating specific error patterns.
     *
     * @param errorType the error classification
     * @param pageable pagination parameters
     * @return page of entries with the specified error type
     */
    Page<DeadLetterQueueEntry> findByErrorType(
        DeadLetterQueueEntry.ErrorType errorType,
        Pageable pageable
    );

    /**
     * Find DLQ entries within a time range.
     * Useful for investigating issues during specific periods.
     *
     * @param from start of time range
     * @param to end of time range
     * @param pageable pagination parameters
     * @return page of entries within the time range
     */
    Page<DeadLetterQueueEntry> findByCreatedAtBetween(
        OffsetDateTime from,
        OffsetDateTime to,
        Pageable pageable
    );

    /**
     * Find DLQ entries by original topic.
     * Useful for investigating topic-specific issues.
     *
     * @param topic the original Kafka topic
     * @param pageable pagination parameters
     * @return page of entries from the specified topic
     */
    Page<DeadLetterQueueEntry> findByOriginalTopic(String topic, Pageable pageable);

    /**
     * Count entries by error type.
     * Useful for metrics and monitoring dashboards.
     *
     * @param errorType the error classification
     * @return count of entries with the specified error type
     */
    long countByErrorType(DeadLetterQueueEntry.ErrorType errorType);

    /**
     * Get counts grouped by error type.
     * Useful for aggregated monitoring/dashboards.
     *
     * @return list of error type counts
     */
    @Query("SELECT e.errorType, COUNT(e) FROM DeadLetterQueueEntry e GROUP BY e.errorType")
    List<Object[]> countByErrorTypeGrouped();
}
