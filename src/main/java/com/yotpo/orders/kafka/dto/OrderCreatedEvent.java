package com.yotpo.orders.kafka.dto;

import com.yotpo.orders.domain.entity.Order;
import com.yotpo.orders.domain.entity.Shopper;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Domain event DTO published to orders.created topic.
 * Published after order is successfully persisted to database.
 *
 * Consumers of this event:
 * - Review request service (to trigger review emails)
 * - Analytics service (to update metrics)
 * - Other downstream services
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreatedEvent {

    /**
     * Event type identifier.
     * Per assignment: Event Name ("orders/created")
     */
    private static final String EVENT_TYPE = "orders/created";

    /**
     * Event metadata.
     * Per assignment: Event Name ("orders/created")
     */
    private String eventName;
    private OffsetDateTime eventTime;

    /**
     * Order data.
     */
    private UUID orderId;
    private String storeId;
    private ShopperInfo shopper;
    private OffsetDateTime orderDate;
    private OffsetDateTime createdAt;
    private List<LineItemInfo> lineItems;
    private BigDecimal totalAmount;

    /**
     * Shopper info for event.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShopperInfo {
        private Long shopperId;
        private String email;
        private String firstName;
        private String lastName;
    }

    /**
     * Line item info for event.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LineItemInfo {
        private Long lineItemId;
        private String externalProductId;
        private String productName;
        private BigDecimal productPrice;
        private Integer quantity;
        private BigDecimal lineTotal;
    }

    /**
     * Create event from persisted Order entity.
     * Shopper is passed explicitly to avoid LazyInitializationException.
     */
    public static OrderCreatedEvent fromEntity(Order order, Shopper shopper) {
        List<LineItemInfo> lineItemInfos = order.getLineItems().stream()
            .map(item -> LineItemInfo.builder()
                .lineItemId(item.getId())
                .externalProductId(item.getExternalProductId())
                .productName(item.getProductName())
                .productPrice(item.getProductPrice())
                .quantity(item.getQuantity())
                .lineTotal(item.getProductPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .build())
            .collect(Collectors.toList());

        BigDecimal total = lineItemInfos.stream()
            .map(LineItemInfo::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return OrderCreatedEvent.builder()
            .eventName(EVENT_TYPE)
            .eventTime(OffsetDateTime.now())
            .orderId(order.getId())
            .storeId(order.getStoreId())
            .shopper(ShopperInfo.builder()
                .shopperId(shopper.getId())
                .email(shopper.getEmail())
                .firstName(shopper.getFirstName())
                .lastName(shopper.getLastName())
                .build())
            .orderDate(order.getOrderDate())
            .createdAt(order.getCreatedAt())
            .lineItems(lineItemInfos)
            .totalAmount(total)
            .build();
    }
}
