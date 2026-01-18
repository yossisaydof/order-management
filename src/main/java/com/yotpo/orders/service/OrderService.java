package com.yotpo.orders.service;

import com.yotpo.orders.api.dto.request.CreateOrderRequest;
import com.yotpo.orders.api.dto.response.OrderListResponse;
import com.yotpo.orders.api.dto.response.OrderResponse;
import com.yotpo.orders.api.exception.OrderNotFoundException;
import com.yotpo.orders.domain.entity.Order;
import com.yotpo.orders.domain.repository.OrderRepository;
import com.yotpo.orders.kafka.dto.OrderMessage;
import com.yotpo.orders.kafka.producer.OrderProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service layer for order operations.
 *
 * Responsibilities:
 * - Orchestrates order creation flow (generate ID, send to Kafka)
 * - Provides order retrieval with store isolation
 * - Handles pagination and filtering for list queries
 *
 * Design Decisions:
 * - UUID generated here for async 202 response pattern
 * - Kafka publish happens synchronously with retry queue fallback
 * - Read operations are transactional for consistency
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /**
     * Maximum page size to prevent abuse.
     */
    private static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderProducer orderProducer;

    public OrderService(OrderRepository orderRepository, OrderProducer orderProducer) {
        this.orderRepository = orderRepository;
        this.orderProducer = orderProducer;
    }

    /**
     * Create a new order (async processing).
     *
     * Generates UUID, sends to Kafka, returns immediately.
     * Actual order creation happens asynchronously via consumer.
     *
     * @param storeId the store identifier
     * @param request the order creation request
     * @return generated order UUID
     */
    public UUID createOrder(String storeId, CreateOrderRequest request) {
        // Generate UUID for async response
        UUID orderId = UUID.randomUUID();

        log.info("Creating order: storeId={}, orderId={}, email={}",
            storeId, orderId, request.getEmail());

        // Create Kafka message
        OrderMessage message = OrderMessage.fromRequest(orderId, storeId, request);

        // Send to Kafka (or queue for retry)
        boolean sent = orderProducer.sendOrderToIncoming(message);

        if (sent) {
            log.info("Order sent to Kafka: orderId={}", orderId);
        } else {
            log.warn("Order queued for retry: orderId={}", orderId);
        }

        return orderId;
    }

    /**
     * Get order by ID within a store.
     *
     * @param storeId the store identifier
     * @param orderId the order UUID
     * @return order response DTO
     * @throws OrderNotFoundException if order not found or wrong store
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(String storeId, UUID orderId) {
        log.debug("Getting order: storeId={}, orderId={}", storeId, orderId);

        Order order = orderRepository.findByIdAndStoreId(orderId, storeId)
            .orElseThrow(() -> new OrderNotFoundException(orderId, storeId));

        return OrderResponse.fromEntity(order);
    }

    /**
     * List orders with optional filters.
     *
     * @param storeId the store identifier
     * @param from start date filter (optional)
     * @param to end date filter (optional)
     * @param email shopper email filter (optional)
     * @param page page number (0-based)
     * @param size page size
     * @return paginated order list
     */
    @Transactional(readOnly = true)
    public OrderListResponse listOrders(String storeId, OffsetDateTime from,
                                        OffsetDateTime to, String email,
                                        int page, int size) {
        log.debug("Listing orders: storeId={}, from={}, to={}, email={}, page={}, size={}",
            storeId, from, to, email, page, size);

        // Limit page size
        size = Math.min(size, MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderDate"));

        Page<Order> orderPage = findOrders(storeId, from, to, email, pageable);
        Page<OrderResponse> responsePage = orderPage.map(OrderResponse::fromEntity);

        return OrderListResponse.fromPage(responsePage);
    }

    /**
     * Check if an order exists.
     *
     * @param orderId the order UUID
     * @return true if order exists
     */
    @Transactional(readOnly = true)
    public boolean orderExists(UUID orderId) {
        return orderRepository.findById(orderId).isPresent();
    }

    /**
     * Find orders based on filter combination.
     */
    private Page<Order> findOrders(String storeId, OffsetDateTime from,
                                   OffsetDateTime to, String email, Pageable pageable) {

        boolean hasDateRange = from != null && to != null;
        boolean hasEmail = email != null && !email.isBlank();

        if (hasDateRange && hasEmail) {
            return orderRepository.findByStoreIdAndOrderDateBetweenAndShopperEmail(
                storeId, from, to, email, pageable);
        } else if (hasDateRange) {
            return orderRepository.findByStoreIdAndOrderDateBetween(
                storeId, from, to, pageable);
        } else if (hasEmail) {
            return orderRepository.findByStoreIdAndShopperEmail(
                storeId, email, pageable);
        } else {
            return orderRepository.findByStoreId(storeId, pageable);
        }
    }
}
