package com.yotpo.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application class for Yotpo Order Management System.
 *
 * This application provides a production-grade order ingestion system that:
 * - Accepts order submissions from merchants via REST API
 * - Processes orders asynchronously using Apache Kafka
 * - Stores order data in PostgreSQL with proper normalization
 * - Publishes domain events for downstream services (review request scheduler)
 *
 * Architecture:
 * Merchant → REST API → Kafka Topic → Consumer → PostgreSQL → Domain Events
 *
 * Key Features:
 * - High availability (retry queue for Kafka failures)
 * - Fault isolation (dead letter queue for poison pills)
 * - At-least-once delivery with idempotency (UUID-based deduplication)
 * - Event-driven architecture for scalability
 */
@SpringBootApplication
@EnableScheduling // Required for RetryQueueProcessor scheduled job
public class OrderManagementApplication {

    /**
     * Application entry point.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(OrderManagementApplication.class, args);
    }

}