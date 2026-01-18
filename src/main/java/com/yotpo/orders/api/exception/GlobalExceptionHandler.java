package com.yotpo.orders.api.exception;

import com.yotpo.orders.api.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Global exception handler for consistent API error responses.
 * Converts exceptions to standardized ErrorResponse format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle validation errors (400 Bad Request).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        BindingResult result = ex.getBindingResult();
        List<ErrorResponse.FieldError> fieldErrors = result.getFieldErrors().stream()
            .map(error -> ErrorResponse.FieldError.builder()
                .field(error.getField())
                .message(error.getDefaultMessage())
                .rejectedValue(error.getRejectedValue())
                .build())
            .collect(Collectors.toList());

        log.warn("Validation failed for request {}: {} errors",
            request.getRequestURI(), fieldErrors.size());

        ErrorResponse response = ErrorResponse.builder()
            .error("VALIDATION_ERROR")
            .message("Request validation failed")
            .status(HttpStatus.BAD_REQUEST.value())
            .path(request.getRequestURI())
            .timestamp(OffsetDateTime.now())
            .details(fieldErrors)
            .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle malformed JSON (400 Bad Request).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        log.warn("Malformed request body for {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
            .error("MALFORMED_REQUEST")
            .message("Request body is malformed or missing")
            .status(HttpStatus.BAD_REQUEST.value())
            .path(request.getRequestURI())
            .timestamp(OffsetDateTime.now())
            .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle missing request parameters (400 Bad Request).
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {

        log.warn("Missing parameter for {}: {}", request.getRequestURI(), ex.getParameterName());

        ErrorResponse response = ErrorResponse.builder()
            .error("MISSING_PARAMETER")
            .message(String.format("Required parameter '%s' is missing", ex.getParameterName()))
            .status(HttpStatus.BAD_REQUEST.value())
            .path(request.getRequestURI())
            .timestamp(OffsetDateTime.now())
            .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle type mismatch (400 Bad Request).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        log.warn("Type mismatch for {}: parameter={}, value={}",
            request.getRequestURI(), ex.getName(), ex.getValue());

        ErrorResponse response = ErrorResponse.builder()
            .error("TYPE_MISMATCH")
            .message(String.format("Parameter '%s' has invalid format", ex.getName()))
            .status(HttpStatus.BAD_REQUEST.value())
            .path(request.getRequestURI())
            .timestamp(OffsetDateTime.now())
            .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle constraint violations from @Validated on controller parameters (400 Bad Request).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        List<ErrorResponse.FieldError> fieldErrors = ex.getConstraintViolations().stream()
            .map(violation -> {
                String path = violation.getPropertyPath().toString();
                // Extract parameter name from path (e.g., "listOrders.page" -> "page")
                String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                return ErrorResponse.FieldError.builder()
                    .field(field)
                    .message(violation.getMessage())
                    .rejectedValue(violation.getInvalidValue())
                    .build();
            })
            .collect(Collectors.toList());

        log.warn("Constraint violation for {}: {} errors",
            request.getRequestURI(), fieldErrors.size());

        ErrorResponse response = ErrorResponse.builder()
            .error("VALIDATION_ERROR")
            .message("Request validation failed")
            .status(HttpStatus.BAD_REQUEST.value())
            .path(request.getRequestURI())
            .timestamp(OffsetDateTime.now())
            .details(fieldErrors)
            .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle order not found (404 Not Found).
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(
            OrderNotFoundException ex,
            HttpServletRequest request) {

        log.info("Order not found: id={}, storeId={}", ex.getOrderId(), ex.getStoreId());

        ErrorResponse response = ErrorResponse.builder()
            .error("ORDER_NOT_FOUND")
            .message(ex.getMessage())
            .status(HttpStatus.NOT_FOUND.value())
            .path(request.getRequestURI())
            .timestamp(OffsetDateTime.now())
            .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle all other exceptions (500 Internal Server Error).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error for {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        ErrorResponse response = ErrorResponse.builder()
            .error("INTERNAL_ERROR")
            .message("An unexpected error occurred")
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .path(request.getRequestURI())
            .timestamp(OffsetDateTime.now())
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
