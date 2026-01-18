package com.yotpo.orders.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Type-safe configuration properties for the application.
 *
 * Binds to properties prefixed with "app" in application.yml.
 * Provides compile-time safety and validation for configuration values.
 *
 * Example:
 *   app.kafka.topics.orders-incoming=orders.incoming
 *   app.retry.interval-ms=5000
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app")
@Validated
public class AppConfigProperties {

    /**
     * Kafka topic configuration.
     */
    private KafkaTopics kafka = new KafkaTopics();

    /**
     * Retry queue configuration.
     */
    private RetryConfig retry = new RetryConfig();

    /**
     * Consumer configuration.
     */
    private ConsumerConfig consumer = new ConsumerConfig();

    /**
     * Kafka topic names configuration.
     */
    @Data
    public static class KafkaTopics {
        private Topics topics = new Topics();
        private ProducerConfig producer = new ProducerConfig();

        @Data
        public static class Topics {
            /**
             * Topic for incoming order messages from API.
             * Default: orders.incoming
             */
            @NotBlank
            private String ordersIncoming = "orders.incoming";

            /**
             * Topic for domain events (order created).
             * Default: orders.created
             */
            @NotBlank
            private String ordersCreated = "orders.created";
        }

        @Data
        public static class ProducerConfig {
            /**
             * Timeout for Kafka send operations (milliseconds).
             * Default: 5000 (5 seconds)
             */
            @Min(1000)
            private long sendTimeoutMs = 5000;
        }
    }

    /**
     * Retry queue processor configuration.
     */
    @Data
    public static class RetryConfig {
        /**
         * Interval between retry queue checks (milliseconds).
         * Default: 5000 (5 seconds)
         */
        @Min(1000)
        private long intervalMs = 5000;

        /**
         * Maximum retry attempts before alerting operations team.
         * Default: 100
         */
        @Min(1)
        private int maxAttempts = 100;

        /**
         * Initial backoff delay (milliseconds).
         * Default: 5000 (5 seconds)
         */
        @Min(1000)
        private long initialBackoffMs = 5000;

        /**
         * Maximum backoff delay (milliseconds).
         * Default: 300000 (5 minutes)
         */
        @Min(1000)
        private long maxBackoffMs = 300000;
    }

    /**
     * Kafka consumer configuration.
     */
    @Data
    public static class ConsumerConfig {
        /**
         * Maximum retries before sending to Dead Letter Queue.
         * Default: 10
         */
        @Min(1)
        private int maxRetries = 10;
    }
}
