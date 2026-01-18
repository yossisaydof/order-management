package com.yotpo.orders.domain.repository;

import com.yotpo.orders.domain.entity.Shopper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Shopper entity.
 *
 * Query Methods:
 * - findByStoreIdAndEmail: Used during order processing to find or create shopper
 */
@Repository
public interface ShopperRepository extends JpaRepository<Shopper, Long> {

    /**
     * Find a shopper by store ID and email.
     * Used for upsert operation during order processing.
     *
     * @param storeId the store identifier
     * @param email the shopper's email address
     * @return the shopper if found
     */
    Optional<Shopper> findByStoreIdAndEmail(String storeId, String email);
}
