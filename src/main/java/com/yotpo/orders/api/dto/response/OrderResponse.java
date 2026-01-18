package com.yotpo.orders.api.dto.response;

import com.yotpo.orders.domain.entity.LineItem;
import com.yotpo.orders.domain.entity.Order;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Response DTO for order details (GET endpoints).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Order details")
public class OrderResponse {

    @Schema(description = "Order ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Store ID", example = "store-123")
    private String storeId;

    @Schema(description = "Shopper information")
    private ShopperInfo shopper;

    @Schema(description = "When the order was placed", example = "2024-01-15T10:30:00Z")
    private OffsetDateTime orderDate;

    @Schema(description = "When the order was processed", example = "2024-01-15T10:30:05Z")
    private OffsetDateTime createdAt;

    @Schema(description = "Products in this order")
    private List<LineItemResponse> lineItems;

    @Schema(description = "Total order value", example = "1999.98")
    private BigDecimal totalAmount;

    /**
     * Shopper information nested DTO.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Shopper details")
    public static class ShopperInfo {
        @Schema(description = "Shopper email", example = "john.doe@example.com")
        private String email;

        @Schema(description = "First name", example = "John")
        private String firstName;

        @Schema(description = "Last name", example = "Doe")
        private String lastName;
    }

    /**
     * Line item nested DTO.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Product in the order")
    public static class LineItemResponse {
        @Schema(description = "Merchant's product ID", example = "PROD-12345")
        private String externalProductId;

        @Schema(description = "Product name", example = "iPhone 15 Pro")
        private String productName;

        @Schema(description = "Product description")
        private String productDescription;

        @Schema(description = "Unit price", example = "999.99")
        private BigDecimal productPrice;

        @Schema(description = "Quantity", example = "2")
        private Integer quantity;

        @Schema(description = "Line total (price * quantity)", example = "1999.98")
        private BigDecimal lineTotal;
    }

    /**
     * Convert entity to response DTO.
     */
    public static OrderResponse fromEntity(Order order) {
        List<LineItemResponse> lineItemResponses = order.getLineItems().stream()
            .map(OrderResponse::mapLineItem)
            .collect(Collectors.toList());

        BigDecimal total = lineItemResponses.stream()
            .map(LineItemResponse::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return OrderResponse.builder()
            .id(order.getId())
            .storeId(order.getStoreId())
            .shopper(ShopperInfo.builder()
                .email(order.getShopper().getEmail())
                .firstName(order.getShopper().getFirstName())
                .lastName(order.getShopper().getLastName())
                .build())
            .orderDate(order.getOrderDate())
            .createdAt(order.getCreatedAt())
            .lineItems(lineItemResponses)
            .totalAmount(total)
            .build();
    }

    private static LineItemResponse mapLineItem(LineItem item) {
        return LineItemResponse.builder()
            .externalProductId(item.getExternalProductId())
            .productName(item.getProductName())
            .productDescription(item.getProductDescription())
            .productPrice(item.getProductPrice())
            .quantity(item.getQuantity())
            .lineTotal(item.getProductPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .build();
    }
}
