# Yotpo Order Management System

A production-grade order management REST API built with Spring Boot, featuring asynchronous order processing via Apache Kafka and PostgreSQL persistence.

## Prerequisites

- **Docker & Docker Compose** — Required
- **jq** — Optional, used for formatted JSON output in API tests

## Quick Start

```bash
# Start the full system (recommended for first-time run)
make run

# The system is ready when you see:
#   Application:  http://localhost:8080
#   Swagger UI:   http://localhost:8080/swagger-ui.html
```

## Stopping Services

```bash
make stop         # Stop services, preserve data volumes
make stop-clean   # Stop services and delete all data
```

## Running Tests

```bash
make test              # Run all tests (unit + integration + API)
make test-unit         # Run unit tests only (fast, no Docker)
make test-integration  # Run integration tests (starts infra automatically)
make test-api          # Run API tests (requires running application)
```

**Test coverage:**
- Unit tests: `OrderServiceTest`, `OrderControllerTest` (25 tests)
- Integration tests: `OrderIntegrationTest` (9 tests)
- API tests: Health, Orders CRUD, Error handling

## Other Commands

```bash
make status    # Check health of all services
make logs      # Follow Docker container logs
make help      # Show all available commands
```

## Architecture Overview

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client    │────▶│  REST API   │────▶│    Kafka    │────▶│  Consumer   │
│  (HTTP)     │     │ (Controller)│     │  (Broker)   │     │ (Processor) │
└─────────────┘     └─────────────┘     └─────────────┘     └──────┬──────┘
                          │                                        │
                          │              ┌─────────────┐           │
                          └─────────────▶│ PostgreSQL  │◀──────────┘
                             (Read)      │  (Database) │   (Write)
                                         └─────────────┘
```

### Key Design Decisions

1. **Asynchronous Processing (202 Accepted Pattern)**
   - API accepts orders immediately and returns a UUID for later retrieval
   - Decouples ingestion from processing, improving throughput and fault tolerance

2. **Retry Queue with Dead Letter Queue (DLQ)**
   - Producer failures: queued in `retry_queue` table with exponential backoff
   - Consumer failures: routed to DLQ after max retries

3. **Multi-tenant by Store ID**
   - All endpoints scoped by `storeId` path parameter
   - Shoppers unique per store (same email can exist across stores)

## Non-Goals & Tradeoffs

| Tradeoff | Rationale |
|----------|-----------|
| **At-least-once delivery** | Exactly-once adds complexity; idempotency via UUID handles duplicates |
| **No distributed transactions** | Eventual consistency is acceptable for this use case |
| **No authentication** | Out of scope; would use JWT/OAuth2 in production |
| **JSON messages** | Simplifies setup; Avro + Schema Registry for production |
| **Single-node Kafka** | Development simplicity; multi-broker for production |

## API Documentation

**Swagger UI**: http://localhost:8080/swagger-ui.html

### Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/{storeId}/orders` | Create order (async, returns 202) |
| GET | `/{storeId}/orders/{orderId}` | Get order by ID |
| GET | `/{storeId}/orders` | List orders with filters |
| GET | `/actuator/health` | Health check |

### Create Order

```bash
curl -X POST http://localhost:8080/store-123/orders \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "orderDate": "2026-01-15T10:00:00Z",
    "lineItems": [
      {
        "externalProductId": "PROD-001",
        "productName": "Wireless Mouse",
        "productPrice": 29.99,
        "quantity": 2
      }
    ]
  }'
```

**Response (202 Accepted):**
```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ACCEPTED",
  "message": "Order accepted for processing"
}
```

### Query Parameters for List Orders

| Parameter | Type | Description |
|-----------|------|-------------|
| `email` | string | Filter by shopper email |
| `from` | ISO datetime | Filter orders from this date |
| `to` | ISO datetime | Filter orders until this date |
| `page` | int | Page number (0-indexed) |
| `size` | int | Page size (default: 20, max: 100) |

## Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Framework | Spring Boot | 3.2.1 |
| Language | Java | 17 |
| Database | PostgreSQL | 15 |
| Messaging | Apache Kafka | KRaft mode |
| Migration | Flyway | 9.x |
| API Docs | SpringDoc OpenAPI | 2.3.0 |

## Project Structure

```
src/main/java/com/yotpo/orders/
├── api/
│   ├── controller/     # REST endpoints
│   ├── dto/            # Request/Response objects
│   └── exception/      # Global exception handling
├── domain/
│   ├── entity/         # JPA entities
│   └── repository/     # Data access
├── kafka/
│   ├── consumer/       # Order processing
│   ├── producer/       # Message publishing
│   └── dto/            # Kafka message schemas
├── service/            # Business logic
└── config/             # Spring configuration
```

## Configuration

### Kafka Topics

| Topic | Purpose |
|-------|---------|
| `orders.incoming` | New orders for processing |
| `orders.created` | Domain events (order persisted) |

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/yotpo_orders` | Database URL |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9093` | Kafka brokers |
| `SERVER_PORT` | `8080` | Application port |

## Future Improvements

1. **Idempotency keys** — Client-provided keys to prevent duplicate order creation
2. **Kafka Schema Registry** — Avro schemas for message contracts and evolution
3. **Exactly-once semantics** — Kafka transactions + outbox pattern
4. **Keyset pagination** — More efficient for large result sets
5. **Consumer scaling** — Partition-based horizontal scaling
6. **Observability** — Distributed tracing, metrics dashboards

## Troubleshooting

| Issue | Solution |
|-------|----------|
| App won't start | Check Docker is running: `docker info` |
| Orders not processing | Check logs: `make logs` |
| Need fresh start | Reset everything: `make stop-clean && make run` |

## License

This project is a take-home assignment for Yotpo.
