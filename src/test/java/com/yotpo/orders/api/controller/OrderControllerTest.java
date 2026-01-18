package com.yotpo.orders.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yotpo.orders.api.dto.request.CreateOrderRequest;
import com.yotpo.orders.api.dto.response.OrderListResponse;
import com.yotpo.orders.api.dto.response.OrderResponse;
import com.yotpo.orders.api.exception.GlobalExceptionHandler;
import com.yotpo.orders.api.exception.OrderNotFoundException;
import com.yotpo.orders.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for OrderController using MockMvc.
 *
 * Tests HTTP layer in isolation with mocked service.
 */
@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("OrderController Unit Tests")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    private static final String STORE_ID = "store-123";
    private static final String BASE_URL = "/" + STORE_ID + "/orders";

    @Nested
    @DisplayName("POST /orders")
    class CreateOrderTests {

        private CreateOrderRequest validRequest;

        @BeforeEach
        void setUp() {
            validRequest = CreateOrderRequest.builder()
                .email("john.doe@example.com")
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
        @DisplayName("should return 202 Accepted with order ID")
        void shouldReturn202WithOrderId() throws Exception {
            // Given
            UUID orderId = UUID.randomUUID();
            when(orderService.createOrder(eq(STORE_ID), any(CreateOrderRequest.class)))
                .thenReturn(orderId);

            // When/Then
            mockMvc.perform(post(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.message").value("Order accepted for processing"));
        }

        @Test
        @DisplayName("should return 400 for missing email")
        void shouldReturn400ForMissingEmail() throws Exception {
            // Given
            validRequest.setEmail(null);

            // When/Then
            mockMvc.perform(post(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[?(@.field=='email')]").exists());
        }

        @Test
        @DisplayName("should return 400 for invalid email format")
        void shouldReturn400ForInvalidEmail() throws Exception {
            // Given
            validRequest.setEmail("invalid-email");

            // When/Then
            mockMvc.perform(post(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[?(@.field=='email')]").exists());
        }

        @Test
        @DisplayName("should return 400 for empty line items")
        void shouldReturn400ForEmptyLineItems() throws Exception {
            // Given
            validRequest.setLineItems(Collections.emptyList());

            // When/Then
            mockMvc.perform(post(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[?(@.field=='lineItems')]").exists());
        }

        @Test
        @DisplayName("should return 400 for future order date")
        void shouldReturn400ForFutureOrderDate() throws Exception {
            // Given
            validRequest.setOrderDate(OffsetDateTime.now().plusDays(1));

            // When/Then
            mockMvc.perform(post(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[?(@.field=='orderDate')]").exists());
        }

        @Test
        @DisplayName("should return 400 for negative price")
        void shouldReturn400ForNegativePrice() throws Exception {
            // Given
            validRequest.getLineItems().get(0).setProductPrice(new BigDecimal("-10.00"));

            // When/Then
            mockMvc.perform(post(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("should return 400 for zero quantity")
        void shouldReturn400ForZeroQuantity() throws Exception {
            // Given
            validRequest.getLineItems().get(0).setQuantity(0);

            // When/Then
            mockMvc.perform(post(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("should return 400 for missing firstName")
        void shouldReturn400ForMissingFirstName() throws Exception {
            // Given
            validRequest.setFirstName(null);

            // When/Then
            mockMvc.perform(post(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[?(@.field=='firstName')]").exists());
        }

        @Test
        @DisplayName("should return 400 for missing lastName")
        void shouldReturn400ForMissingLastName() throws Exception {
            // Given
            validRequest.setLastName(null);

            // When/Then
            mockMvc.perform(post(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[?(@.field=='lastName')]").exists());
        }

        @Test
        @DisplayName("should return 400 for quantity exceeding maximum")
        void shouldReturn400ForQuantityExceedingMax() throws Exception {
            // Given - Max quantity is 10,000
            validRequest.getLineItems().get(0).setQuantity(10001);

            // When/Then
            mockMvc.perform(post(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("should return 400 for missing product name")
        void shouldReturn400ForMissingProductName() throws Exception {
            // Given
            validRequest.getLineItems().get(0).setProductName(null);

            // When/Then
            mockMvc.perform(post(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("should return 400 for missing external product ID")
        void shouldReturn400ForMissingExternalProductId() throws Exception {
            // Given
            validRequest.getLineItems().get(0).setExternalProductId(null);

            // When/Then
            mockMvc.perform(post(BASE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("GET /orders/{orderId}")
    class GetOrderTests {

        private UUID orderId;
        private OrderResponse orderResponse;

        @BeforeEach
        void setUp() {
            orderId = UUID.randomUUID();
            orderResponse = OrderResponse.builder()
                .id(orderId)
                .storeId(STORE_ID)
                .shopper(OrderResponse.ShopperInfo.builder()
                    .email("john.doe@example.com")
                    .firstName("John")
                    .lastName("Doe")
                    .build())
                .orderDate(OffsetDateTime.now().minusDays(1))
                .createdAt(OffsetDateTime.now())
                .lineItems(List.of(
                    OrderResponse.LineItemResponse.builder()
                        .externalProductId("PROD-001")
                        .productName("iPhone 15")
                        .productPrice(new BigDecimal("999.99"))
                        .quantity(1)
                        .lineTotal(new BigDecimal("999.99"))
                        .build()
                ))
                .totalAmount(new BigDecimal("999.99"))
                .build();
        }

        @Test
        @DisplayName("should return 200 with order details")
        void shouldReturn200WithOrderDetails() throws Exception {
            // Given
            when(orderService.getOrder(STORE_ID, orderId)).thenReturn(orderResponse);

            // When/Then
            mockMvc.perform(get(BASE_URL + "/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.storeId").value(STORE_ID))
                .andExpect(jsonPath("$.shopper.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.lineItems").isArray())
                .andExpect(jsonPath("$.lineItems[0].productName").value("iPhone 15"))
                .andExpect(jsonPath("$.totalAmount").value(999.99));
        }

        @Test
        @DisplayName("should return 404 when order not found")
        void shouldReturn404WhenNotFound() throws Exception {
            // Given
            when(orderService.getOrder(STORE_ID, orderId))
                .thenThrow(new OrderNotFoundException(orderId, STORE_ID));

            // When/Then
            mockMvc.perform(get(BASE_URL + "/" + orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("should return 400 for invalid UUID")
        void shouldReturn400ForInvalidUuid() throws Exception {
            // When/Then
            mockMvc.perform(get(BASE_URL + "/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("TYPE_MISMATCH"));
        }
    }

    @Nested
    @DisplayName("GET /orders")
    class ListOrdersTests {

        @Test
        @DisplayName("should return 200 with empty list")
        void shouldReturn200WithEmptyList() throws Exception {
            // Given
            OrderListResponse response = OrderListResponse.builder()
                .orders(Collections.emptyList())
                .pagination(OrderListResponse.PageInfo.builder()
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .first(true)
                    .last(true)
                    .build())
                .build();

            when(orderService.listOrders(eq(STORE_ID), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(response);

            // When/Then
            mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").isArray())
                .andExpect(jsonPath("$.orders").isEmpty())
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.totalElements").value(0));
        }

        @Test
        @DisplayName("should accept pagination parameters")
        void shouldAcceptPaginationParameters() throws Exception {
            // Given
            OrderListResponse response = OrderListResponse.builder()
                .orders(Collections.emptyList())
                .pagination(OrderListResponse.PageInfo.builder()
                    .page(2)
                    .size(50)
                    .totalElements(0)
                    .totalPages(0)
                    .first(false)
                    .last(true)
                    .build())
                .build();

            when(orderService.listOrders(eq(STORE_ID), isNull(), isNull(), isNull(), eq(2), eq(50)))
                .thenReturn(response);

            // When/Then
            mockMvc.perform(get(BASE_URL)
                    .param("page", "2")
                    .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.page").value(2))
                .andExpect(jsonPath("$.pagination.size").value(50));
        }

        @Test
        @DisplayName("should accept email filter")
        void shouldAcceptEmailFilter() throws Exception {
            // Given
            String email = "test@example.com";
            OrderListResponse response = OrderListResponse.builder()
                .orders(Collections.emptyList())
                .pagination(OrderListResponse.PageInfo.builder()
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .first(true)
                    .last(true)
                    .build())
                .build();

            when(orderService.listOrders(eq(STORE_ID), isNull(), isNull(), eq(email), eq(0), eq(20)))
                .thenReturn(response);

            // When/Then
            mockMvc.perform(get(BASE_URL)
                    .param("email", email))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 400 for negative page number")
        void shouldReturn400ForNegativePage() throws Exception {
            // When/Then
            mockMvc.perform(get(BASE_URL)
                    .param("page", "-1"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for zero page size")
        void shouldReturn400ForZeroPageSize() throws Exception {
            // When/Then
            mockMvc.perform(get(BASE_URL)
                    .param("size", "0"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for page size exceeding maximum")
        void shouldReturn400ForPageSizeExceedingMax() throws Exception {
            // When/Then - Max size is 100
            mockMvc.perform(get(BASE_URL)
                    .param("size", "101"))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Store ID Validation")
    class StoreIdValidationTests {

        @Test
        @DisplayName("should return 400 for store ID with spaces")
        void shouldReturn400ForStoreIdWithSpaces() throws Exception {
            // When/Then
            mockMvc.perform(get("/store with spaces/orders"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for store ID with special characters")
        void shouldReturn400ForStoreIdWithSpecialChars() throws Exception {
            // When/Then
            mockMvc.perform(get("/store@123/orders"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should accept store ID with hyphens and underscores")
        void shouldAcceptStoreIdWithHyphensAndUnderscores() throws Exception {
            // Given
            OrderListResponse response = OrderListResponse.builder()
                .orders(Collections.emptyList())
                .pagination(OrderListResponse.PageInfo.builder()
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .first(true)
                    .last(true)
                    .build())
                .build();

            when(orderService.listOrders(eq("store-123_test"), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(response);

            // When/Then
            mockMvc.perform(get("/store-123_test/orders"))
                .andExpect(status().isOk());
        }
    }
}
