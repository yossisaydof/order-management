package com.yotpo.orders.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.UUID;

/**
 * Response DTO for order creation (202 Accepted).
 * Returns immediately with order ID for async tracking.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response after order submission (async processing)")
public class CreateOrderResponse {

    @Schema(description = "Generated order ID for tracking", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID orderId;

    @Schema(description = "Processing status", example = "ACCEPTED")
    private String status;

    @Schema(description = "Human-readable message", example = "Order accepted for processing")
    private String message;

    /**
     * Create a standard accepted response.
     */
    public static CreateOrderResponse accepted(UUID orderId) {
        return CreateOrderResponse.builder()
            .orderId(orderId)
            .status("ACCEPTED")
            .message("Order accepted for processing")
            .build();
    }
}
