package com.yotpo.orders.integration;

import com.yotpo.orders.api.dto.request.CreateOrderRequest;
import com.yotpo.orders.domain.entity.Order;
import com.yotpo.orders.domain.entity.Shopper;
import com.yotpo.orders.domain.repository.OrderRepository;
import com.yotpo.orders.domain.repository.ShopperRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end integration tests for order management.
 *
 * Uses the running Docker Compose containers (PostgreSQL + Kafka).
 * Requires Docker Compose to be running before tests.
 *
 * Tests the complete flow:
 * 1. REST API receives order
 * 2. Kafka message is published
 * 3. Consumer processes message
 * 4. Order is persisted to database
 * 5. Order can be retrieved via API
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Order Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class OrderIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ShopperRepository shopperRepository;

    private String baseUrl;
    private static final String STORE_ID = "integration-test-store";

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/" + STORE_ID + "/orders";
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Health check should return UP")
    void healthCheckShouldReturnUp() {
        String healthUrl = "http://localhost:" + port + "/actuator/health";

        ResponseEntity<Map> response = restTemplate.getForEntity(healthUrl, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("POST /orders should return 202 Accepted and process order asynchronously")
    void createOrderShouldReturn202AndProcessAsync() {
        // Given
        CreateOrderRequest request = CreateOrderRequest.builder()
            .email("integration-test@example.com")
            .firstName("Integration")
            .lastName("Test")
            .orderDate(OffsetDateTime.now().minusHours(1))
            .lineItems(List.of(
                CreateOrderRequest.LineItemRequest.builder()
                    .externalProductId("PROD-INT-001")
                    .productName("Test Product")
                    .productDescription("Product for integration testing")
                    .productPrice(new BigDecimal("49.99"))
                    .quantity(2)
                    .build()
            ))
            .build();

        // When
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl, entity, Map.class);

        // Then - API returns 202 immediately
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsKey("orderId");
        assertThat(response.getBody().get("status")).isEqualTo("ACCEPTED");

        String orderIdStr = (String) response.getBody().get("orderId");
        UUID orderId = UUID.fromString(orderIdStr);

        // Wait for async processing to complete
        await()
            .atMost(30, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> {
                assertThat(orderRepository.findById(orderId)).isPresent();
            });

        // Verify order was persisted correctly via API (avoids LazyInitializationException)
        ResponseEntity<Map> getResponse = restTemplate.getForEntity(
            baseUrl + "/" + orderId, Map.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("storeId")).isEqualTo(STORE_ID);

        Map<String, Object> shopper = (Map<String, Object>) getResponse.getBody().get("shopper");
        assertThat(shopper.get("email")).isEqualTo("integration-test@example.com");

        List<Map<String, Object>> lineItems = (List<Map<String, Object>>) getResponse.getBody().get("lineItems");
        assertThat(lineItems).hasSize(1);
        assertThat(lineItems.get(0).get("productName")).isEqualTo("Test Product");
        assertThat(((Number) lineItems.get(0).get("quantity")).intValue()).isEqualTo(2);
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("GET /orders/{orderId} should return order details")
    void getOrderShouldReturnOrderDetails() {
        // Given - Create and wait for an order to be processed
        CreateOrderRequest request = CreateOrderRequest.builder()
            .email("get-test@example.com")
            .firstName("Get")
            .lastName("Test")
            .orderDate(OffsetDateTime.now().minusHours(2))
            .lineItems(List.of(
                CreateOrderRequest.LineItemRequest.builder()
                    .externalProductId("PROD-GET-001")
                    .productName("Get Test Product")
                    .productPrice(new BigDecimal("99.99"))
                    .quantity(1)
                    .build()
            ))
            .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> createResponse = restTemplate.postForEntity(baseUrl, entity, Map.class);
        String orderIdStr = (String) createResponse.getBody().get("orderId");
        UUID orderId = UUID.fromString(orderIdStr);

        // Wait for async processing
        await()
            .atMost(30, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> {
                assertThat(orderRepository.findById(orderId)).isPresent();
            });

        // When - GET the order
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/" + orderId, Map.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("id")).isEqualTo(orderIdStr);
        assertThat(response.getBody().get("storeId")).isEqualTo(STORE_ID);

        Map<String, Object> shopper = (Map<String, Object>) response.getBody().get("shopper");
        assertThat(shopper.get("email")).isEqualTo("get-test@example.com");

        List<Map<String, Object>> lineItems = (List<Map<String, Object>>) response.getBody().get("lineItems");
        assertThat(lineItems).hasSize(1);
        assertThat(lineItems.get(0).get("productName")).isEqualTo("Get Test Product");
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("GET /orders should return paginated list")
    void listOrdersShouldReturnPaginatedList() {
        // Given - Ensure we have at least one order from previous tests
        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                assertThat(orderRepository.findByStoreId(STORE_ID,
                    org.springframework.data.domain.PageRequest.of(0, 10))
                    .getTotalElements()).isGreaterThan(0);
            });

        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "?page=0&size=10", Map.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> orders = (List<Map<String, Object>>) response.getBody().get("orders");
        assertThat(orders).isNotEmpty();

        Map<String, Object> pagination = (Map<String, Object>) response.getBody().get("pagination");
        assertThat(pagination.get("page")).isEqualTo(0);
        assertThat((Integer) pagination.get("totalElements")).isGreaterThan(0);
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("GET /orders with email filter should return filtered results")
    void listOrdersWithEmailFilterShouldReturnFilteredResults() {
        // Given - Create order with unique email
        String uniqueEmail = "filter-test-" + System.currentTimeMillis() + "@example.com";
        CreateOrderRequest request = CreateOrderRequest.builder()
            .email(uniqueEmail)
            .firstName("Filter")
            .lastName("Test")
            .orderDate(OffsetDateTime.now().minusMinutes(30))
            .lineItems(List.of(
                CreateOrderRequest.LineItemRequest.builder()
                    .externalProductId("PROD-FILTER-001")
                    .productName("Filter Test Product")
                    .productPrice(new BigDecimal("29.99"))
                    .quantity(1)
                    .build()
            ))
            .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> createResponse = restTemplate.postForEntity(baseUrl, entity, Map.class);
        String orderIdStr = (String) createResponse.getBody().get("orderId");
        UUID orderId = UUID.fromString(orderIdStr);

        // Wait for async processing
        await()
            .atMost(30, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> {
                assertThat(orderRepository.findById(orderId)).isPresent();
            });

        // When - Filter by email
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "?email=" + uniqueEmail, Map.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> orders = (List<Map<String, Object>>) response.getBody().get("orders");
        assertThat(orders).hasSize(1);

        Map<String, Object> shopper = (Map<String, Object>) orders.get(0).get("shopper");
        assertThat(shopper.get("email")).isEqualTo(uniqueEmail);
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("GET /orders/{orderId} with non-existent ID should return 404")
    void getOrderWithNonExistentIdShouldReturn404() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/" + nonExistentId, Map.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("error")).isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("POST /orders with invalid request should return 400")
    void createOrderWithInvalidRequestShouldReturn400() {
        // Given - Missing required fields
        CreateOrderRequest request = CreateOrderRequest.builder()
            .email("invalid") // Invalid email format
            .firstName("Test")
            .lastName("User")
            .orderDate(OffsetDateTime.now().plusDays(1)) // Future date
            .lineItems(List.of()) // Empty line items
            .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(request, headers);

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl, entity, Map.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Shopper should be reused for same email within store")
    void shopperShouldBeReusedForSameEmail() {
        // Given - Create first order
        String sharedEmail = "shared-" + System.currentTimeMillis() + "@example.com";

        CreateOrderRequest request1 = CreateOrderRequest.builder()
            .email(sharedEmail)
            .firstName("First")
            .lastName("Order")
            .orderDate(OffsetDateTime.now().minusHours(3))
            .lineItems(List.of(
                CreateOrderRequest.LineItemRequest.builder()
                    .externalProductId("PROD-SHARED-001")
                    .productName("Shared Product 1")
                    .productPrice(new BigDecimal("19.99"))
                    .quantity(1)
                    .build()
            ))
            .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CreateOrderRequest> entity1 = new HttpEntity<>(request1, headers);

        ResponseEntity<Map> response1 = restTemplate.postForEntity(baseUrl, entity1, Map.class);
        UUID orderId1 = UUID.fromString((String) response1.getBody().get("orderId"));

        // Wait for first order
        await()
            .atMost(30, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> {
                assertThat(orderRepository.findById(orderId1)).isPresent();
            });

        // Create second order with same email
        CreateOrderRequest request2 = CreateOrderRequest.builder()
            .email(sharedEmail)
            .firstName("Second")
            .lastName("Order")
            .orderDate(OffsetDateTime.now().minusHours(2))
            .lineItems(List.of(
                CreateOrderRequest.LineItemRequest.builder()
                    .externalProductId("PROD-SHARED-002")
                    .productName("Shared Product 2")
                    .productPrice(new BigDecimal("29.99"))
                    .quantity(2)
                    .build()
            ))
            .build();

        HttpEntity<CreateOrderRequest> entity2 = new HttpEntity<>(request2, headers);
        ResponseEntity<Map> response2 = restTemplate.postForEntity(baseUrl, entity2, Map.class);
        UUID orderId2 = UUID.fromString((String) response2.getBody().get("orderId"));

        // Wait for second order
        await()
            .atMost(30, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> {
                assertThat(orderRepository.findById(orderId2)).isPresent();
            });

        // Then - Both orders should reference the same shopper
        Order order1 = orderRepository.findById(orderId1).orElseThrow();
        Order order2 = orderRepository.findById(orderId2).orElseThrow();

        assertThat(order1.getShopper().getId()).isEqualTo(order2.getShopper().getId());

        // Shopper name should be updated to latest
        Shopper shopper = shopperRepository.findByStoreIdAndEmail(STORE_ID, sharedEmail).orElseThrow();
        assertThat(shopper.getFirstName()).isEqualTo("Second");
        assertThat(shopper.getLastName()).isEqualTo("Order");
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Order total should be calculated correctly")
    void orderTotalShouldBeCalculatedCorrectly() {
        // Given
        CreateOrderRequest request = CreateOrderRequest.builder()
            .email("total-test@example.com")
            .firstName("Total")
            .lastName("Test")
            .orderDate(OffsetDateTime.now().minusHours(1))
            .lineItems(List.of(
                CreateOrderRequest.LineItemRequest.builder()
                    .externalProductId("PROD-TOTAL-001")
                    .productName("Product A")
                    .productPrice(new BigDecimal("10.00"))
                    .quantity(3) // 30.00
                    .build(),
                CreateOrderRequest.LineItemRequest.builder()
                    .externalProductId("PROD-TOTAL-002")
                    .productName("Product B")
                    .productPrice(new BigDecimal("25.50"))
                    .quantity(2) // 51.00
                    .build()
            ))
            .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> createResponse = restTemplate.postForEntity(baseUrl, entity, Map.class);
        String orderIdStr = (String) createResponse.getBody().get("orderId");
        UUID orderId = UUID.fromString(orderIdStr);

        // Wait for async processing
        await()
            .atMost(30, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> {
                assertThat(orderRepository.findById(orderId)).isPresent();
            });

        // When - GET the order
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/" + orderId, Map.class);

        // Then - Verify totals
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Total should be 30.00 + 51.00 = 81.00
        Number totalAmount = (Number) response.getBody().get("totalAmount");
        assertThat(totalAmount.doubleValue()).isEqualTo(81.00);

        List<Map<String, Object>> lineItems = (List<Map<String, Object>>) response.getBody().get("lineItems");
        assertThat(lineItems).hasSize(2);

        // Verify line totals
        Number lineTotal1 = (Number) lineItems.get(0).get("lineTotal");
        Number lineTotal2 = (Number) lineItems.get(1).get("lineTotal");
        assertThat(lineTotal1.doubleValue() + lineTotal2.doubleValue()).isEqualTo(81.00);
    }
}
