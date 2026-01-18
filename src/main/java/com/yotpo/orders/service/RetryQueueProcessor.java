package com.yotpo.orders.service;

import com.yotpo.orders.config.AppConfigProperties;
import com.yotpo.orders.domain.entity.RetryQueueEntry;
import com.yotpo.orders.domain.repository.RetryQueueRepository;
import com.yotpo.orders.kafka.producer.OrderProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Background processor for retry queue.
 *
 * Runs on a fixed schedule to retry failed Kafka publishes.
 * Uses exponential backoff to prevent overwhelming Kafka during outages.
 *
 * Processing Flow:
 * 1. Query entries where next_retry_at <= now
 * 2. Attempt to send each to Kafka
 * 3. On success: delete entry
 * 4. On failure: increment retry_count, update next_retry_at
 */
@Service
public class RetryQueueProcessor {

    private static final Logger log = LoggerFactory.getLogger(RetryQueueProcessor.class);

    /**
     * Maximum entries to process per batch.
     */
    private static final int BATCH_SIZE = 100;

    private final RetryQueueRepository retryQueueRepository;
    private final OrderProducer orderProducer;
    private final AppConfigProperties config;

    public RetryQueueProcessor(
            RetryQueueRepository retryQueueRepository,
            OrderProducer orderProducer,
            AppConfigProperties config) {
        this.retryQueueRepository = retryQueueRepository;
        this.orderProducer = orderProducer;
        this.config = config;
    }

    /**
     * Process retry queue on schedule.
     * Default: every 5 seconds (configurable via app.retry.interval-ms)
     */
    @Scheduled(fixedDelayString = "${app.retry.interval-ms:5000}")
    @Transactional
    public void processRetryQueue() {
        OffsetDateTime now = OffsetDateTime.now();
        int maxRetries = config.getRetry().getMaxAttempts();

        // Get entries ready for retry
        List<RetryQueueEntry> entries = retryQueueRepository.findEntriesReadyForRetry(
            now, maxRetries, PageRequest.of(0, BATCH_SIZE));

        if (entries.isEmpty()) {
            return; // Nothing to process
        }

        log.info("Processing {} retry queue entries", entries.size());

        int successCount = 0;
        int failCount = 0;

        for (RetryQueueEntry entry : entries) {
            try {
                boolean success = orderProducer.retrySend(entry);

                if (success) {
                    // Delete successful entry
                    retryQueueRepository.delete(entry);
                    successCount++;
                    log.debug("Retry successful, entry deleted: orderId={}", entry.getOrderId());
                } else {
                    // Update for next retry with exponential backoff
                    updateForNextRetry(entry);
                    failCount++;
                }

            } catch (Exception e) {
                log.error("Error processing retry entry: orderId={}", entry.getOrderId(), e);
                updateForNextRetry(entry);
                entry.setLastError(e.getMessage());
                failCount++;
            }
        }

        log.info("Retry queue processing complete: success={}, failed={}", successCount, failCount);
    }

    /**
     * Update entry for next retry with exponential backoff.
     */
    private void updateForNextRetry(RetryQueueEntry entry) {
        int baseDelaySeconds = (int) (config.getRetry().getInitialBackoffMs() / 1000);
        int maxDelaySeconds = (int) (config.getRetry().getMaxBackoffMs() / 1000);

        entry.incrementRetryWithBackoff(baseDelaySeconds, maxDelaySeconds);
        retryQueueRepository.save(entry);

        log.debug("Entry scheduled for retry: orderId={}, retryCount={}, nextRetryAt={}",
            entry.getOrderId(), entry.getRetryCount(), entry.getNextRetryAt());
    }
}
