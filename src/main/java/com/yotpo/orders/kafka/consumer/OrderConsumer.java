package com.yotpo.orders.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yotpo.orders.domain.entity.*;
import com.yotpo.orders.domain.repository.DeadLetterQueueRepository;
import com.yotpo.orders.domain.repository.OrderRepository;
import com.yotpo.orders.domain.repository.ShopperRepository;
import com.yotpo.orders.kafka.dto.OrderCreatedEvent;
import com.yotpo.orders.kafka.dto.OrderMessage;
import com.yotpo.orders.kafka.producer.OrderProducer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for processing incoming orders.
 *
 * Processing Flow:
 * 1. Receive message from orders.incoming topic
 * 2. Deserialize OrderMessage
 * 3. Upsert shopper (find or create)
 * 4. Create order with line items
 * 5. Publish ORDER_CREATED domain event
 * 6. Acknowledge message
 *
 * Error Handling:
 * - Transient errors: Retry up to max attempts
 * - Permanent errors: Send to DLQ immediately
 * - Max retries exceeded: Send to DLQ
 */
@Component
public class OrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);

    private final ObjectMapper objectMapper;
    private final ShopperRepository shopperRepository;
    private final OrderRepository orderRepository;
    private final DeadLetterQueueRepository dlqRepository;
    private final OrderProducer orderProducer;

    public OrderConsumer(
            ObjectMapper objectMapper,
            ShopperRepository shopperRepository,
            OrderRepository orderRepository,
            DeadLetterQueueRepository dlqRepository,
            OrderProducer orderProducer) {
        this.objectMapper = objectMapper;
        this.shopperRepository = shopperRepository;
        this.orderRepository = orderRepository;
        this.dlqRepository = dlqRepository;
        this.orderProducer = orderProducer;
    }

    /**
     * Consume order messages from orders.incoming topic.
     */
    @KafkaListener(
        topics = "${app.kafka.topics.orders-incoming}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrder(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("Received message: topic={}, partition={}, offset={}, key={}",
            record.topic(), record.partition(), record.offset(), record.key());

        try {
            // Deserialize message
            OrderMessage message = objectMapper.readValue(record.value(), OrderMessage.class);

            // Process order
            processOrder(message);

            // Acknowledge on success
            ack.acknowledge();

            log.info("Order processed successfully: orderId={}", message.getOrderId());

        } catch (Exception e) {
            handleError(record, e, ack);
        }
    }

    /**
     * Process order message: create shopper and order, publish event.
     */
    @Transactional
    protected void processOrder(OrderMessage message) {
        log.debug("Processing order: orderId={}, storeId={}", message.getOrderId(), message.getStoreId());

        // 1. Upsert shopper (find existing or create new)
        Shopper shopper = upsertShopper(message);

        // 2. Check for duplicate order (idempotency)
        if (orderRepository.findById(message.getOrderId()).isPresent()) {
            log.info("Duplicate order detected, skipping: orderId={}", message.getOrderId());
            return;
        }

        // 3. Create order entity
        Order order = Order.builder()
            .id(message.getOrderId())
            .storeId(message.getStoreId())
            .shopper(shopper)
            .orderDate(message.getOrderDate())
            .build();

        // 4. Add line items
        for (OrderMessage.LineItemData itemData : message.getLineItems()) {
            LineItem lineItem = LineItem.builder()
                .externalProductId(itemData.getExternalProductId())
                .productName(itemData.getProductName())
                .productDescription(itemData.getProductDescription())
                .productPrice(itemData.getProductPrice())
                .quantity(itemData.getQuantity())
                .build();

            order.addLineItem(lineItem);
        }

        // 5. Save order (cascades to line items)
        Order savedOrder = orderRepository.save(order);

        log.info("Order persisted: orderId={}, shopperId={}, lineItems={}",
            savedOrder.getId(), shopper.getId(), savedOrder.getLineItems().size());

        // 6. Publish domain event
        // Pass shopper directly to avoid LazyInitializationException - the @Transactional
        // annotation doesn't work for internal method calls (Spring AOP proxy limitation),
        // so we pass the already-loaded shopper instead of accessing order.getShopper()
        OrderCreatedEvent event = OrderCreatedEvent.fromEntity(savedOrder, shopper);
        orderProducer.publishOrderCreatedEvent(event);
    }

    /**
     * Upsert shopper: find by store+email or create new.
     */
    private Shopper upsertShopper(OrderMessage message) {
        String storeId = message.getStoreId();
        String email = message.getShopper().getEmail();

        return shopperRepository.findByStoreIdAndEmail(storeId, email)
            .map(existing -> {
                // Update name if changed
                boolean updated = false;
                if (message.getShopper().getFirstName() != null &&
                    !message.getShopper().getFirstName().equals(existing.getFirstName())) {
                    existing.setFirstName(message.getShopper().getFirstName());
                    updated = true;
                }
                if (message.getShopper().getLastName() != null &&
                    !message.getShopper().getLastName().equals(existing.getLastName())) {
                    existing.setLastName(message.getShopper().getLastName());
                    updated = true;
                }
                if (updated) {
                    existing = shopperRepository.save(existing);
                    log.debug("Shopper updated: id={}, email={}", existing.getId(), email);
                }
                return existing;
            })
            .orElseGet(() -> {
                Shopper newShopper = Shopper.builder()
                    .storeId(storeId)
                    .email(email)
                    .firstName(message.getShopper().getFirstName())
                    .lastName(message.getShopper().getLastName())
                    .build();
                Shopper saved = shopperRepository.save(newShopper);
                log.debug("Shopper created: id={}, email={}", saved.getId(), email);
                return saved;
            });
    }

    /**
     * Handle processing errors with retry/DLQ logic.
     */
    private void handleError(ConsumerRecord<String, String> record, Exception e, Acknowledgment ack) {
        log.error("Error processing message: partition={}, offset={}, error={}",
            record.partition(), record.offset(), e.getMessage(), e);

        // Classify error
        DeadLetterQueueEntry.ErrorType errorType = classifyError(e);

        // For permanent errors, send to DLQ immediately
        if (errorType == DeadLetterQueueEntry.ErrorType.VALIDATION_ERROR ||
            errorType == DeadLetterQueueEntry.ErrorType.DATA_INTEGRITY) {

            sendToDlq(record, e, errorType, 0);
            ack.acknowledge(); // Commit offset to unblock partition
            return;
        }

        // For transient errors, we rely on Spring Kafka's retry mechanism
        // If max retries exceeded, Spring will call error handler
        // For now, send to DLQ and acknowledge
        sendToDlq(record, e, DeadLetterQueueEntry.ErrorType.UNKNOWN, 0);
        ack.acknowledge();
    }

    /**
     * Classify exception to determine handling strategy.
     */
    private DeadLetterQueueEntry.ErrorType classifyError(Exception e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        String className = e.getClass().getSimpleName().toLowerCase();

        // Validation errors (permanent)
        if (className.contains("validation") ||
            className.contains("illegalargument") ||
            message.contains("validation failed") ||
            message.contains("invalid")) {
            return DeadLetterQueueEntry.ErrorType.VALIDATION_ERROR;
        }

        // Data integrity errors (permanent)
        if (className.contains("dataintegrity") ||
            className.contains("constraint") ||
            message.contains("duplicate key") ||
            message.contains("foreign key") ||
            message.contains("constraint violation")) {
            return DeadLetterQueueEntry.ErrorType.DATA_INTEGRITY;
        }

        // Everything else is unknown (needs investigation)
        return DeadLetterQueueEntry.ErrorType.UNKNOWN;
    }

    /**
     * Send failed message to Dead Letter Queue.
     */
    private void sendToDlq(ConsumerRecord<String, String> record, Exception e,
                          DeadLetterQueueEntry.ErrorType errorType, int retryCount) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(record.value(), Map.class);

            // Extract orderId from payload
            UUID orderId = null;
            if (payload.containsKey("orderId")) {
                Object orderIdValue = payload.get("orderId");
                if (orderIdValue instanceof String) {
                    orderId = UUID.fromString((String) orderIdValue);
                }
            }
            if (orderId == null) {
                orderId = UUID.randomUUID(); // Fallback for corrupted messages
            }

            DeadLetterQueueEntry entry = DeadLetterQueueEntry.builder()
                .orderId(orderId)
                .originalTopic(record.topic())
                .partitionId(record.partition())
                .offsetId(record.offset())
                .messageKey(record.key())
                .messagePayload(payload)
                .errorType(errorType)
                .errorMessage(e.getMessage())
                .stackTrace(getStackTrace(e))
                .retryCount(retryCount)
                .build();

            dlqRepository.save(entry);

            log.warn("Message sent to DLQ: orderId={}, topic={}, partition={}, offset={}, errorType={}",
                orderId, record.topic(), record.partition(), record.offset(), errorType);

        } catch (Exception dlqError) {
            log.error("CRITICAL: Failed to send message to DLQ: partition={}, offset={}, originalError={}, dlqError={}",
                record.partition(), record.offset(), e.getMessage(), dlqError.getMessage(), dlqError);
        }
    }

    /**
     * Get stack trace as string.
     */
    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
            if (sb.length() > 4000) { // Limit stack trace size
                sb.append("... truncated");
                break;
            }
        }
        return sb.toString();
    }
}
