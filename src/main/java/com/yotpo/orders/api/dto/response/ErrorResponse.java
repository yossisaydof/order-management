package com.yotpo.orders.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Standard error response format for API errors.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Error response")
public class ErrorResponse {

    @Schema(description = "Error code", example = "VALIDATION_ERROR")
    private String error;

    @Schema(description = "Human-readable error message", example = "Request validation failed")
    private String message;

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "Request path", example = "/store-123/orders")
    private String path;

    @Schema(description = "When the error occurred")
    private OffsetDateTime timestamp;

    @Schema(description = "Detailed validation errors (if applicable)")
    private List<FieldError> details;

    /**
     * Field-level validation error.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Field validation error")
    public static class FieldError {
        @Schema(description = "Field name", example = "email")
        private String field;

        @Schema(description = "Error message", example = "must not be blank")
        private String message;

        @Schema(description = "Rejected value", example = "")
        private Object rejectedValue;
    }
}
