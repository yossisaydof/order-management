package com.yotpo.orders.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Shopper entity representing a customer who places orders.
 *
 * Design Decision: NORMALIZED
 * - Shoppers are first-class entities that Yotpo tracks across orders
 * - Unique by (store_id, email) - same email in different stores = different shoppers
 * - Names can be updated (merchant might correct typos across orders)
 *
 * Business Context:
 * - Yotpo sends review requests to shoppers
 * - Need to track shopper behavior across multiple orders
 * - Email is the natural unique identifier within a store
 */
@Entity
@Table(
    name = "shoppers",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_shoppers_store_email",
            columnNames = {"store_id", "email"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shopper {

    /**
     * Primary key - auto-generated sequential ID.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Store identifier (merchant's store).
     * Not a foreign key - stores are managed externally by Yotpo platform.
     */
    @Column(name = "store_id", nullable = false, length = 255)
    private String storeId;

    /**
     * Shopper email - unique identifier within a store.
     * Used to identify returning customers across orders.
     */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /**
     * Shopper's first name.
     * May be updated if merchant provides corrected name in later orders.
     */
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    /**
     * Shopper's last name.
     * May be updated if merchant provides corrected name in later orders.
     */
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /**
     * When the shopper record was created (first order).
     */
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * When the shopper record was last updated.
     * Updated when name changes in subsequent orders.
     */
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Set timestamps before persisting new entity.
     */
    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Update timestamp before updating existing entity.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
