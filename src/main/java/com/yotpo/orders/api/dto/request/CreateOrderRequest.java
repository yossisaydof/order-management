package com.yotpo.orders.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Request DTO for creating a new order.
 * Validates all required fields according to business rules.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to create a new order")
public class CreateOrderRequest {

    @NotBlank(message = "Shopper email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    @Schema(description = "Shopper's email address", example = "john.doe@example.com")
    private String email;

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    @Schema(description = "Shopper's first name", example = "John")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    @Schema(description = "Shopper's last name", example = "Doe")
    private String lastName;

    @NotNull(message = "Order date is required")
    @PastOrPresent(message = "Order date cannot be in the future")
    @Schema(description = "When the order was placed", example = "2024-01-15T10:30:00Z")
    private OffsetDateTime orderDate;

    @NotEmpty(message = "At least one line item is required")
    @Size(max = 100, message = "Order cannot have more than 100 line items")
    @Valid
    @Schema(description = "Products purchased in this order (1-100 items)")
    private List<LineItemRequest> lineItems;

    /**
     * Line item within an order request.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Product purchased in the order")
    public static class LineItemRequest {

        @NotBlank(message = "External product ID is required")
        @Size(max = 255, message = "External product ID must not exceed 255 characters")
        @Schema(description = "Merchant's product identifier", example = "PROD-12345")
        private String externalProductId;

        @NotBlank(message = "Product name is required")
        @Size(max = 500, message = "Product name must not exceed 500 characters")
        @Schema(description = "Product name at time of purchase", example = "iPhone 15 Pro")
        private String productName;

        @Size(max = 2000, message = "Product description must not exceed 2000 characters")
        @Schema(description = "Product description", example = "Latest Apple smartphone with titanium design")
        private String productDescription;

        @NotNull(message = "Product price is required")
        @DecimalMin(value = "0.00", message = "Product price must be non-negative")
        @Digits(integer = 8, fraction = 2, message = "Price format: up to 8 digits and 2 decimal places")
        @Schema(description = "Price per unit at time of purchase", example = "999.99")
        private BigDecimal productPrice;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 10000, message = "Quantity cannot exceed 10,000")
        @Schema(description = "Number of units purchased (1-10,000)", example = "2")
        private Integer quantity;
    }
}
