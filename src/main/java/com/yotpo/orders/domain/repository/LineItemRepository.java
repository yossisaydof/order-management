package com.yotpo.orders.domain.repository;

import com.yotpo.orders.domain.entity.LineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for LineItem entity.
 *
 * Note: Line items are typically accessed through the Order entity
 * (cascade loading). This repository provides direct access if needed.
 */
@Repository
public interface LineItemRepository extends JpaRepository<LineItem, Long> {

    /**
     * Find all line items for an order.
     * Useful for batch operations or when Order entity not loaded.
     *
     * @param orderId the order UUID
     * @return list of line items for the order
     */
    List<LineItem> findByOrderId(UUID orderId);
}
