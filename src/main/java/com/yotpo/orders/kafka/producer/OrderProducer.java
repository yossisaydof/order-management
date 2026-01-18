package com.yotpo.orders.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yotpo.orders.config.AppConfigProperties;
import com.yotpo.orders.domain.entity.RetryQueueEntry;
import com.yotpo.orders.domain.repository.RetryQueueRepository;
import com.yotpo.orders.kafka.dto.OrderCreatedEvent;
import com.yotpo.orders.kafka.dto.OrderMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer for order-related messages.
 *
 * Responsibilities:
 * 1. Publish incoming orders to orders.incoming topic
 * 2. Publish domain events to orders.created topic
 * 3. Handle publish failures with retry queue
 *
 * High Availability:
 * - If Kafka publish fails, order is queued in retry_queue table
 * - Background job retries with exponential backoff
 * - API always returns 202 Accepted
 */
@Component
public class OrderProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final AppConfigProperties config;
    private final RetryQueueRepository retryQueueRepository;

    public OrderProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            AppConfigProperties config,
            RetryQueueRepository retryQueueRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.config = config;
        this.retryQueueRepository = retryQueueRepository;
    }

    /**
     * Send order to incoming topic for processing.
     * On failure, queues to retry_queue for background retry.
     *
     * @param message the order message to send
     * @return true if sent successfully, false if queued for retry
     */
    public boolean sendOrderToIncoming(OrderMessage message) {
        String topic = config.getKafka().getTopics().getOrdersIncoming();
        String key = message.getStoreId(); // Partition by store for ordering

        try {
            String payload = objectMapper.writeValueAsString(message);

            CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(topic, key, payload);

            // Wait for result with timeout
            SendResult<String, String> result = future.get(
                config.getKafka().getProducer().getSendTimeoutMs(),
                java.util.concurrent.TimeUnit.MILLISECONDS
            );

            log.info("Order sent to Kafka: orderId={}, topic={}, partition={}, offset={}",
                message.getOrderId(),
                result.getRecordMetadata().topic(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());

            return true;

        } catch (JsonProcessingException e) {
            // Serialization error - should not happen with valid data
            log.error("Failed to serialize order message: orderId={}", message.getOrderId(), e);
            queueForRetry(message, "Serialization error: " + e.getMessage());
            return false;

        } catch (Exception e) {
            // Kafka unavailable or other error - queue for retry
            log.warn("Failed to send order to Kafka, queuing for retry: orderId={}, error={}",
                message.getOrderId(), e.getMessage());
            queueForRetry(message, e.getMessage());
            return false;
        }
    }

    /**
     * Publish ORDER_CREATED domain event after successful persistence.
     *
     * @param event the domain event to publish
     */
    public void publishOrderCreatedEvent(OrderCreatedEvent event) {
        String topic = config.getKafka().getTopics().getOrdersCreated();
        String key = event.getStoreId();

        try {
            String payload = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ORDER_CREATED event: orderId={}",
                            event.getOrderId(), ex);
                    } else {
                        log.info("ORDER_CREATED event published: orderId={}, partition={}, offset={}",
                            event.getOrderId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                    }
                });

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ORDER_CREATED event: orderId={}", event.getOrderId(), e);
        }
    }

    /**
     * Retry sending a message from retry queue.
     *
     * @param entry the retry queue entry
     * @return true if successful, false if failed
     */
    public boolean retrySend(RetryQueueEntry entry) {
        String topic = config.getKafka().getTopics().getOrdersIncoming();
        String key = entry.getStoreId();

        try {
            String payload = objectMapper.writeValueAsString(entry.getMessagePayload());

            CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(topic, key, payload);

            SendResult<String, String> result = future.get(
                config.getKafka().getProducer().getSendTimeoutMs(),
                java.util.concurrent.TimeUnit.MILLISECONDS
            );

            log.info("Retry successful: orderId={}, partition={}, offset={}",
                entry.getOrderId(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());

            return true;

        } catch (Exception e) {
            log.warn("Retry failed: orderId={}, attempt={}, error={}",
                entry.getOrderId(), entry.getRetryCount() + 1, e.getMessage());
            return false;
        }
    }

    /**
     * Queue failed message for background retry.
     */
    private void queueForRetry(OrderMessage message, String errorMessage) {
        try {
            // Convert message to Map for JSONB storage
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.convertValue(message, Map.class);

            RetryQueueEntry entry = RetryQueueEntry.builder()
                .orderId(message.getOrderId())
                .storeId(message.getStoreId())
                .messagePayload(payload)
                .retryCount(0)
                .nextRetryAt(OffsetDateTime.now())
                .lastError(errorMessage)
                .build();

            retryQueueRepository.save(entry);

            log.info("Order queued for retry: orderId={}", message.getOrderId());

        } catch (Exception e) {
            // Critical: Cannot queue for retry - log for manual intervention
            log.error("CRITICAL: Failed to queue order for retry: orderId={}, originalError={}, queueError={}",
                message.getOrderId(), errorMessage, e.getMessage(), e);
        }
    }
}
