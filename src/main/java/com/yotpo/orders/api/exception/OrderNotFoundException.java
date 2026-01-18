package com.yotpo.orders.api.exception;

import java.util.UUID;

/**
 * Exception thrown when an order is not found.
 */
public class OrderNotFoundException extends RuntimeException {

    private final UUID orderId;
    private final String storeId;

    public OrderNotFoundException(UUID orderId, String storeId) {
        super(String.format("Order not found: id=%s, storeId=%s", orderId, storeId));
        this.orderId = orderId;
        this.storeId = storeId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getStoreId() {
        return storeId;
    }
}
