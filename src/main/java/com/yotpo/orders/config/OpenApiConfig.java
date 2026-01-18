package com.yotpo.orders.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI (Swagger) configuration for API documentation.
 *
 * Provides comprehensive API documentation accessible at:
 *   - Swagger UI: http://localhost:8080/swagger-ui.html
 *   - OpenAPI JSON: http://localhost:8080/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI yotpoOrderManagementOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Server"),
                        new Server()
                                .url("http://yotpo-app:8080")
                                .description("Docker Container")
                ))
                .tags(List.of(
                        new Tag()
                                .name("Orders")
                                .description("Order management endpoints - submit, retrieve, and filter orders")
                ));
    }

    private Info apiInfo() {
        return new Info()
                .title("Yotpo Order Management API")
                .description("""
                        ## Overview

                        Production-grade order ingestion system for Yotpo's review request platform.

                        This API allows merchants to:
                        - Submit orders for processing (async via Kafka)
                        - Retrieve individual orders by ID
                        - Filter and paginate orders

                        ## Architecture

                        ```
                        Merchant → REST API → Kafka → Consumer → PostgreSQL → Domain Events
                        ```

                        ## Key Features

                        - **High Availability**: Orders are always accepted (internal retry queue)
                        - **Async Processing**: 202 Accepted response pattern
                        - **Idempotency**: UUID-based deduplication prevents duplicate orders
                        - **Event-Driven**: Domain events published for downstream services

                        ## Response Codes

                        | Code | Description |
                        |------|-------------|
                        | 200 | Success (GET requests) |
                        | 202 | Accepted (POST - order queued for processing) |
                        | 400 | Bad Request (validation errors) |
                        | 404 | Not Found (order doesn't exist) |
                        | 500 | Internal Server Error |

                        ## Date Format

                        All dates use ISO 8601 format with timezone:
                        - `2025-01-14T10:30:00Z` (UTC)
                        - `2025-01-14T10:30:00+02:00` (with offset)
                        """)
                .version("1.0.0");
    }
}
