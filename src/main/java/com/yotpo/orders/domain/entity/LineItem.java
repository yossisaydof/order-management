package com.yotpo.orders.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Line item entity representing a purchased product in an order.
 *
 * Design Decisions:
 * - DENORMALIZED: Product data is NOT linked to a products table
 * - Reason: Orders are immutable historical records
 * - Must preserve price/name at time of purchase
 * - external_product_id is merchant's ID, not Yotpo's
 *
 * Business Context:
 * - If merchant changes product from "$799 iPhone 13" to
 *   "$699 iPhone 13 Refurbished", old orders must still
 *   show the original $799 price.
 */
@Entity
@Table(name = "line_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineItem {

    /**
     * Primary key - auto-generated sequential ID.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /**
     * Reference to the order this line item belongs to.
     * Many line items can belong to one order.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "order_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_line_items_order")
    )
    private Order order;

    /**
     * Merchant's product identifier (external to Yotpo).
     * This is metadata, NOT a foreign key to a products table.
     */
    @Column(name = "external_product_id", nullable = false, length = 255)
    private String externalProductId;

    /**
     * Product name at time of purchase (SNAPSHOT - immutable).
     */
    @Column(name = "product_name", nullable = false, length = 500)
    private String productName;

    /**
     * Product description at time of purchase (SNAPSHOT - immutable).
     */
    @Column(name = "product_description", columnDefinition = "TEXT")
    private String productDescription;

    /**
     * Price at time of purchase (SNAPSHOT - immutable).
     * NUMERIC(10,2) allows up to 99,999,999.99
     */
    @Column(name = "product_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal productPrice;

    /**
     * Quantity purchased.
     */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
}
