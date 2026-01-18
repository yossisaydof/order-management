package com.yotpo.orders.api.controller;

import com.yotpo.orders.api.dto.request.CreateOrderRequest;
import com.yotpo.orders.api.dto.response.CreateOrderResponse;
import com.yotpo.orders.api.dto.response.ErrorResponse;
import com.yotpo.orders.api.dto.response.OrderListResponse;
import com.yotpo.orders.api.dto.response.OrderResponse;
import com.yotpo.orders.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.springframework.validation.annotation.Validated;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * REST controller for order management.
 *
 * Endpoints:
 * - POST /{storeId}/orders - Create new order (async, returns 202)
 * - GET /{storeId}/orders/{orderId} - Get order by ID
 * - GET /{storeId}/orders - List orders with filters
 *
 * All endpoints are scoped to a store (merchant isolation).
 */
@RestController
@RequestMapping("/{storeId}/orders")
@Tag(name = "Orders", description = "Order management API")
@Validated
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Create a new order (async processing).
     *
     * Returns 202 Accepted immediately with order ID.
     * Order is processed asynchronously via Kafka.
     */
    @PostMapping
    @Operation(summary = "Create order", description = "Submit order for async processing. Returns immediately with order ID.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Order accepted for processing",
            content = @Content(schema = @Schema(implementation = CreateOrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CreateOrderResponse> createOrder(
            @Parameter(description = "Store identifier (alphanumeric, hyphens, underscores)", example = "store-123")
            @PathVariable
            @Size(min = 1, max = 100, message = "Store ID must be between 1 and 100 characters")
            @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Store ID must be alphanumeric (hyphens and underscores allowed)")
            String storeId,
            @Valid @RequestBody CreateOrderRequest request) {

        log.info("Received order request: storeId={}, email={}", storeId, request.getEmail());

        UUID orderId = orderService.createOrder(storeId, request);

        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(CreateOrderResponse.accepted(orderId));
    }

    /**
     * Get order by ID.
     *
     * Returns 404 if order not found or belongs to different store.
     */
    @GetMapping("/{orderId}")
    @Operation(summary = "Get order", description = "Retrieve order details by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order found",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "404", description = "Order not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OrderResponse> getOrder(
            @Parameter(description = "Store identifier (alphanumeric, hyphens, underscores)", example = "store-123")
            @PathVariable
            @Size(min = 1, max = 100, message = "Store ID must be between 1 and 100 characters")
            @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Store ID must be alphanumeric (hyphens and underscores allowed)")
            String storeId,
            @Parameter(description = "Order ID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID orderId) {

        log.debug("Get order request: storeId={}, orderId={}", storeId, orderId);

        OrderResponse response = orderService.getOrder(storeId, orderId);

        return ResponseEntity.ok(response);
    }

    /**
     * List orders with optional filters.
     *
     * Supports filtering by date range and shopper email.
     * Results are paginated and sorted by order date (newest first).
     */
    @GetMapping
    @Operation(summary = "List orders", description = "List orders with optional filters and pagination")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Orders retrieved",
            content = @Content(schema = @Schema(implementation = OrderListResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OrderListResponse> listOrders(
            @Parameter(description = "Store identifier (alphanumeric, hyphens, underscores)", example = "store-123")
            @PathVariable
            @Size(min = 1, max = 100, message = "Store ID must be between 1 and 100 characters")
            @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Store ID must be alphanumeric (hyphens and underscores allowed)")
            String storeId,

            @Parameter(description = "Filter by start date (inclusive)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,

            @Parameter(description = "Filter by end date (inclusive)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to,

            @Parameter(description = "Filter by shopper email")
            @RequestParam(required = false)
            String email,

            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number cannot be negative")
            int page,

            @Parameter(description = "Page size (1-100)", example = "20")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size cannot exceed 100")
            int size) {

        log.debug("List orders request: storeId={}, from={}, to={}, email={}, page={}, size={}",
            storeId, from, to, email, page, size);

        OrderListResponse response = orderService.listOrders(storeId, from, to, email, page, size);

        return ResponseEntity.ok(response);
    }
}
