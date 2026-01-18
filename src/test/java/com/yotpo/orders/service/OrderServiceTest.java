package com.yotpo.orders.service;

import com.yotpo.orders.api.dto.request.CreateOrderRequest;
import com.yotpo.orders.api.dto.response.OrderListResponse;
import com.yotpo.orders.api.dto.response.OrderResponse;
import com.yotpo.orders.api.exception.OrderNotFoundException;
import com.yotpo.orders.domain.entity.LineItem;
import com.yotpo.orders.domain.entity.Order;
import com.yotpo.orders.domain.entity.Shopper;
import com.yotpo.orders.domain.repository.OrderRepository;
import com.yotpo.orders.kafka.producer.OrderProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderService.
 *
 * Tests business logic in isolation using mocks.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Unit Tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderProducer orderProducer;

    @InjectMocks
    private OrderService orderService;

    private static final String STORE_ID = "store-123";
    private static final String EMAIL = "john.doe@example.com";

    @Nested
    @DisplayName("createOrder")
    class CreateOrderTests {

        private CreateOrderRequest validRequest;

        @BeforeEach
        void setUp() {
            validRequest = CreateOrderRequest.builder()
                .email(EMAIL)
                .firstName("John")
                .lastName("Doe")
                .orderDate(OffsetDateTime.now().minusDays(1))
                .lineItems(List.of(
                    CreateOrderRequest.LineItemRequest.builder()
                        .externalProductId("PROD-001")
                        .productName("iPhone 15")
                        .productPrice(new BigDecimal("999.99"))
                        .quantity(1)
                        .build()
                ))
                .build();
        }

        @Test
        @DisplayName("should generate UUID and send to Kafka")
        void shouldGenerateUuidAndSendToKafka() {
            // Given
            when(orderProducer.sendOrderToIncoming(any())).thenReturn(true);

            // When
            UUID orderId = orderService.createOrder(STORE_ID, validRequest);

            // Then
            assertThat(orderId).isNotNull();
            verify(orderProducer).sendOrderToIncoming(any());
        }

        @Test
        @DisplayName("should return orderId even when Kafka fails")
        void shouldReturnOrderIdWhenKafkaFails() {
            // Given - Kafka send fails (queued for retry)
            when(orderProducer.sendOrderToIncoming(any())).thenReturn(false);

            // When
            UUID orderId = orderService.createOrder(STORE_ID, validRequest);

            // Then - Still returns UUID (high availability)
            assertThat(orderId).isNotNull();
            verify(orderProducer).sendOrderToIncoming(any());
        }
    }

    @Nested
    @DisplayName("getOrder")
    class GetOrderTests {

        private UUID orderId;
        private Order order;

        @BeforeEach
        void setUp() {
            orderId = UUID.randomUUID();

            Shopper shopper = Shopper.builder()
                .id(1L)
                .storeId(STORE_ID)
                .email(EMAIL)
                .firstName("John")
                .lastName("Doe")
                .build();

            order = Order.builder()
                .id(orderId)
                .storeId(STORE_ID)
                .shopper(shopper)
                .orderDate(OffsetDateTime.now().minusDays(1))
                .createdAt(OffsetDateTime.now())
                .lineItems(List.of(
                    LineItem.builder()
                        .id(1L)
                        .externalProductId("PROD-001")
                        .productName("iPhone 15")
                        .productPrice(new BigDecimal("999.99"))
                        .quantity(1)
                        .build()
                ))
                .build();
        }

        @Test
        @DisplayName("should return order when found")
        void shouldReturnOrderWhenFound() {
            // Given
            when(orderRepository.findByIdAndStoreId(orderId, STORE_ID))
                .thenReturn(Optional.of(order));

            // When
            OrderResponse response = orderService.getOrder(STORE_ID, orderId);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(orderId);
            assertThat(response.getStoreId()).isEqualTo(STORE_ID);
            assertThat(response.getShopper().getEmail()).isEqualTo(EMAIL);
            assertThat(response.getLineItems()).hasSize(1);
        }

        @Test
        @DisplayName("should throw OrderNotFoundException when not found")
        void shouldThrowWhenOrderNotFound() {
            // Given
            when(orderRepository.findByIdAndStoreId(orderId, STORE_ID))
                .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> orderService.getOrder(STORE_ID, orderId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString())
                .hasMessageContaining(STORE_ID);
        }

        @Test
        @DisplayName("should not return order from different store")
        void shouldNotReturnOrderFromDifferentStore() {
            // Given - Order exists but in different store
            String differentStoreId = "different-store";
            when(orderRepository.findByIdAndStoreId(orderId, differentStoreId))
                .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> orderService.getOrder(differentStoreId, orderId))
                .isInstanceOf(OrderNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("listOrders")
    class ListOrdersTests {

        @Test
        @DisplayName("should return paginated list")
        void shouldReturnPaginatedList() {
            // Given
            Pageable pageable = PageRequest.of(0, 20);
            Page<Order> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
            when(orderRepository.findByStoreId(eq(STORE_ID), any(Pageable.class)))
                .thenReturn(emptyPage);

            // When
            OrderListResponse response = orderService.listOrders(
                STORE_ID, null, null, null, 0, 20);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getOrders()).isEmpty();
            assertThat(response.getPagination().getPage()).isEqualTo(0);
            assertThat(response.getPagination().getSize()).isEqualTo(20);
        }

        @Test
        @DisplayName("should filter by date range")
        void shouldFilterByDateRange() {
            // Given
            OffsetDateTime from = OffsetDateTime.now().minusDays(7);
            OffsetDateTime to = OffsetDateTime.now();
            Page<Order> emptyPage = new PageImpl<>(Collections.emptyList());

            when(orderRepository.findByStoreIdAndOrderDateBetween(
                eq(STORE_ID), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(emptyPage);

            // When
            orderService.listOrders(STORE_ID, from, to, null, 0, 20);

            // Then
            verify(orderRepository).findByStoreIdAndOrderDateBetween(
                eq(STORE_ID), eq(from), eq(to), any(Pageable.class));
        }

        @Test
        @DisplayName("should filter by email")
        void shouldFilterByEmail() {
            // Given
            Page<Order> emptyPage = new PageImpl<>(Collections.emptyList());

            when(orderRepository.findByStoreIdAndShopperEmail(
                eq(STORE_ID), eq(EMAIL), any(Pageable.class)))
                .thenReturn(emptyPage);

            // When
            orderService.listOrders(STORE_ID, null, null, EMAIL, 0, 20);

            // Then
            verify(orderRepository).findByStoreIdAndShopperEmail(
                eq(STORE_ID), eq(EMAIL), any(Pageable.class));
        }

        @Test
        @DisplayName("should filter by date range and email")
        void shouldFilterByDateRangeAndEmail() {
            // Given
            OffsetDateTime from = OffsetDateTime.now().minusDays(7);
            OffsetDateTime to = OffsetDateTime.now();
            Page<Order> emptyPage = new PageImpl<>(Collections.emptyList());

            when(orderRepository.findByStoreIdAndOrderDateBetweenAndShopperEmail(
                eq(STORE_ID), eq(from), eq(to), eq(EMAIL), any(Pageable.class)))
                .thenReturn(emptyPage);

            // When
            orderService.listOrders(STORE_ID, from, to, EMAIL, 0, 20);

            // Then
            verify(orderRepository).findByStoreIdAndOrderDateBetweenAndShopperEmail(
                eq(STORE_ID), eq(from), eq(to), eq(EMAIL), any(Pageable.class));
        }

        @Test
        @DisplayName("should limit page size to max")
        void shouldLimitPageSizeToMax() {
            // Given
            Page<Order> emptyPage = new PageImpl<>(Collections.emptyList());
            when(orderRepository.findByStoreId(eq(STORE_ID), any(Pageable.class)))
                .thenReturn(emptyPage);

            // When - Request 500, should be limited to 100
            orderService.listOrders(STORE_ID, null, null, null, 0, 500);

            // Then - Verify pageable has max size
            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(orderRepository).findByStoreId(eq(STORE_ID), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("orderExists")
    class OrderExistsTests {

        @Test
        @DisplayName("should return true when order exists")
        void shouldReturnTrueWhenExists() {
            // Given
            UUID orderId = UUID.randomUUID();
            when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(mock(Order.class)));

            // When
            boolean exists = orderService.orderExists(orderId);

            // Then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should return false when order does not exist")
        void shouldReturnFalseWhenNotExists() {
            // Given
            UUID orderId = UUID.randomUUID();
            when(orderRepository.findById(orderId))
                .thenReturn(Optional.empty());

            // When
            boolean exists = orderService.orderExists(orderId);

            // Then
            assertThat(exists).isFalse();
        }
    }
}
