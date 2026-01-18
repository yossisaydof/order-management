package com.yotpo.orders.domain.repository;

import com.yotpo.orders.domain.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Order entity.
 *
 * Query Methods:
 * - findByIdAndStoreId: GET /orders/{id} with store isolation
 * - findByStoreIdAndOrderDateBetween: GET /orders?from=&to= with date range
 * - findByStoreIdAndShopperEmail: GET /orders?email= filter
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Find order by ID within a specific store.
     * Ensures store isolation (merchant can only see their orders).
     *
     * @param id the order UUID
     * @param storeId the store identifier
     * @return the order if found within the store
     */
    Optional<Order> findByIdAndStoreId(UUID id, String storeId);

    /**
     * Find orders within a date range for a store.
     * Supports: GET /{store_id}/orders?from=&to=
     *
     * @param storeId the store identifier
     * @param from start of date range (inclusive)
     * @param to end of date range (inclusive)
     * @param pageable pagination parameters
     * @return page of orders within the date range
     */
    Page<Order> findByStoreIdAndOrderDateBetween(
        String storeId,
        OffsetDateTime from,
        OffsetDateTime to,
        Pageable pageable
    );

    /**
     * Find orders by store with optional shopper email filter.
     * Uses JOIN to filter by shopper email.
     *
     * @param storeId the store identifier
     * @param email the shopper's email address
     * @param pageable pagination parameters
     * @return page of orders matching the criteria
     */
    @Query("SELECT o FROM Order o JOIN o.shopper s " +
           "WHERE o.storeId = :storeId AND s.email = :email")
    Page<Order> findByStoreIdAndShopperEmail(
        @Param("storeId") String storeId,
        @Param("email") String email,
        Pageable pageable
    );

    /**
     * Find orders by store with optional date range and shopper email filter.
     * Combined filter for advanced queries.
     *
     * @param storeId the store identifier
     * @param from start of date range (inclusive)
     * @param to end of date range (inclusive)
     * @param email the shopper's email address
     * @param pageable pagination parameters
     * @return page of orders matching all criteria
     */
    @Query("SELECT o FROM Order o JOIN o.shopper s " +
           "WHERE o.storeId = :storeId " +
           "AND o.orderDate BETWEEN :from AND :to " +
           "AND s.email = :email")
    Page<Order> findByStoreIdAndOrderDateBetweenAndShopperEmail(
        @Param("storeId") String storeId,
        @Param("from") OffsetDateTime from,
        @Param("to") OffsetDateTime to,
        @Param("email") String email,
        Pageable pageable
    );

    /**
     * Find all orders for a store with pagination.
     * Supports: GET /{store_id}/orders (no filters)
     *
     * @param storeId the store identifier
     * @param pageable pagination parameters
     * @return page of orders for the store
     */
    Page<Order> findByStoreId(String storeId, Pageable pageable);
}
