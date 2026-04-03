# Order Payment System

An event-driven microservice-style application built with **Spring Boot 3**, **Apache Kafka**, and **PostgreSQL**.  
Orders flow through Kafka topics for fully async payment processing and notifications.

---

## Architecture

```
┌──────────────┐     POST /api/orders      ┌─────────────────────────────────────┐
│    Client    │ ────────────────────────► │          Spring Boot App             │
└──────────────┘                           │                                     │
                                           │  ┌──────────┐   ┌────────────────┐ │
                                           │  │  Order   │   │    Security    │ │
                                           │  │Controller│   │  (Basic Auth)  │ │
                                           │  └────┬─────┘   └────────────────┘ │
                                           │       │                             │
                                           │  ┌────▼─────┐                      │
                                           │  │  Order   │                      │
                                           │  │ Service  │                      │
                                           │  └────┬─────┘                      │
                                           │       │ save(PENDING)               │
                                           │  ┌────▼──────────┐                 │
                                           │  │ OrderProducer │                 │
                                           └──┴───────┬────────┴─────────────────┘
                                                       │
                                            ┌──────────▼──────────┐
                                            │    order-topic      │
                                            │    (3 partitions)   │
                                            │    key = orderId    │
                                            └──────────┬──────────┘
                                                       │
                                            ┌──────────▼──────────┐
                                            │  PaymentConsumer    │
                                            │  (payment-group)    │
                                            │  + Idempotency      │
                                            └──────┬──────┬───────┘
                                                   │      │
                                          80%      │      │   20%
                                       success     │      │  failure
                                                   │      │
                              ┌────────────────────┘      └────────────────────┐
                              │                                                 │
                   ┌──────────▼──────────┐                       ┌─────────────▼───────────┐
                   │    payment-topic    │                       │  payment-failed-topic   │
                   │    (3 partitions)   │                       │    (3 partitions)       │
                   └──────────┬──────────┘                       └─────────────┬───────────┘
                              │                                                 │
                   ┌──────────▼──────────────────────────────────────────────► │
                   │         NotificationConsumer (notification-group)          │
                   │         + Idempotency guard                                │
                   └────────────────────────────────────────────────────────────┘
                              │                                    │
                    ✅ Success email/SMS               ❌ Failure email/SMS
```

### Kafka Topics

| Topic                 | Partitions | Producer          | Consumer               |
|-----------------------|------------|-------------------|------------------------|
| `order-topic`         | 3          | OrderProducer     | PaymentConsumer        |
| `payment-topic`       | 3          | PaymentProducer   | NotificationConsumer   |
| `payment-failed-topic`| 3          | PaymentProducer   | NotificationConsumer   |
| `notification-topic`  | 3          | (reserved)        | (reserved)             |
| `*.DLT`               | auto       | Spring Kafka DLT  | Manual inspection      |

---

## How Kafka Enables Parallel Processing

In a traditional system, every order is processed one by one in a single thread — a bottleneck.

With Kafka:

1. **Topics are split into partitions** — each partition is an independent, ordered queue.  
   This system uses **3 partitions per topic**.

2. **orderId is the message key** — Kafka routes all messages with the same key to the same partition.  
   This guarantees ordering for a given order (e.g. payment before notification).

3. **Consumer groups scale horizontally** — `payment-group` can run up to **3 parallel consumers**
   (one per partition). Add more app instances and Kafka auto-rebalances partitions across them.

4. **To scale beyond 3** — increase `KAFKA_NUM_PARTITIONS` and add more app instances.  
   No code changes required.

```
Partition 0 → Consumer Thread 1  (handles orders for ~1/3 of customers)
Partition 1 → Consumer Thread 2  (handles orders for ~1/3 of customers)
Partition 2 → Consumer Thread 3  (handles orders for ~1/3 of customers)
```

---

## Environment Variables

| Variable                  | Description                          | Default (dev only)     |
|---------------------------|--------------------------------------|------------------------|
| `DB_HOST`                 | PostgreSQL host                      | `localhost`            |
| `DB_PORT`                 | PostgreSQL port                      | `5432`                 |
| `DB_NAME`                 | Database name                        | `orderpayment`         |
| `DB_USERNAME`             | Database username                    | `postgres`             |
| `DB_PASSWORD`             | Database password                    | **set in .env**        |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address                 | `localhost:9092`       |
| `SECURITY_ADMIN_USER`     | Admin username (Basic Auth)          | `admin`                |
| `SECURITY_ADMIN_PASSWORD` | Admin password (Basic Auth)          | **set in .env**        |
| `SECURITY_USER_USER`      | Regular user username                | `user`                 |
| `SECURITY_USER_PASSWORD`  | Regular user password                | **set in .env**        |
| `DOCKER_REGISTRY`         | Docker Hub username / registry       | —                      |
| `IMAGE_TAG`               | Docker image tag                     | `latest`               |

---

## Setup & Run

### Prerequisites

- Java 17+
- Maven 3.9+
- Docker & Docker Compose

### Local (without Docker)

```bash
# 1. Start infrastructure
docker-compose up -d zookeeper kafka postgres

# 2. Copy and fill environment variables
cp .env .env
# Edit .env with your values

# 3. Export env vars to shell
export $(grep -v '^#' .env | xargs)

# 4. Run the app
mvn spring-boot:run
```

### Full Docker

```bash
# 1. Copy and configure .env
cp .env .env
# Edit .env — especially passwords and DOCKER_REGISTRY

# 2. Build the Docker image
docker build -t your-dockerhub-username/order-payment-system:latest .

# 3. Start all services
docker-compose up -d

# 4. Check logs
docker-compose logs -f app

# 5. Verify health
curl http://localhost:8080/actuator/health
```

### Run Tests

```bash
# Unit tests only (no Docker needed)
mvn test -pl . -Dtest="OrderServiceTest,PaymentServiceTest"

# All tests including integration (Docker required for Testcontainers)
mvn verify
```

---

## API Reference

### Base URL
```
http://localhost:8080
```

### Authentication
All `/api/**` endpoints use **HTTP Basic Auth**.

| User    | Password       | Role        |
|---------|----------------|-------------|
| `admin` | from `.env`    | ADMIN, USER |
| `user`  | from `.env`    | USER        |

---

### POST /api/orders — Create Order

```bash
curl -X POST http://localhost:8080/api/orders \
  -u user:your_user_password \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "customerEmail": "john.doe@example.com",
    "productName": "Wireless Headphones",
    "quantity": 2,
    "amount": 199.99,
    "idempotencyKey": "order-req-abc-123"
  }'
```

**Response 201 Created:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "customerId": "CUST-001",
  "customerEmail": "john.doe@example.com",
  "productName": "Wireless Headphones",
  "quantity": 2,
  "amount": 199.99,
  "status": "PAYMENT_PROCESSING",
  "idempotencyKey": "order-req-abc-123",
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-01-15T10:30:00"
}
```

---

### GET /api/orders/{orderId} — Get Order

```bash
curl http://localhost:8080/api/orders/550e8400-e29b-41d4-a716-446655440000 \
  -u user:your_user_password
```

**Response 200 OK:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PAYMENT_SUCCESS",
  ...
}
```

---

### GET /api/orders — List All Orders (Admin only)

```bash
curl http://localhost:8080/api/orders \
  -u admin:your_admin_password
```

---

### GET /api/orders/customer/{customerId} — Orders by Customer

```bash
curl http://localhost:8080/api/orders/customer/CUST-001 \
  -u user:your_user_password
```

---

### GET /actuator/health — Health Check (no auth)

```bash
curl http://localhost:8080/actuator/health
```

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "kafka": { "status": "UP" }
  }
}
```

---

## Swagger UI

Available at: **http://localhost:8080/swagger-ui.html** (no auth required)

OpenAPI JSON: **http://localhost:8080/api-docs**

---

## Order Status Transitions

```
PENDING ──────────────────────────────► CANCELLED
   │
   ▼
PAYMENT_PROCESSING
   │
   ├──────────────────────────────────► PAYMENT_SUCCESS (terminal)
   │
   └──────────────────────────────────► PAYMENT_FAILED  (terminal)
```

Any invalid transition (e.g. PAYMENT_SUCCESS → PAYMENT_PROCESSING) returns **409 Conflict**.

---

## Idempotency

**Client-side:** Pass an `idempotencyKey` in the request body. If a duplicate key is detected, the API returns **409 Conflict** instead of creating a duplicate order.

**Kafka consumer-side:** Every consumed event is stored in the `processed_events` table (`eventId + topic`). On redelivery, the event is skipped and logged.

---

## Error Handling & Retry

| Scenario                       | Behaviour                                              |
|--------------------------------|--------------------------------------------------------|
| Kafka consumer throws          | Retry with exponential backoff (1s → 2s → 4s)         |
| Retries exhausted              | Message sent to `<topic>.DLT` for manual review        |
| Duplicate Kafka message        | Detected via `processed_events` table, silently skipped|
| API validation failure         | 400 Bad Request with `fieldErrors` map                 |
| Order not found                | 404 Not Found (RFC 7807 ProblemDetail format)          |
| Invalid state transition       | 409 Conflict                                           |

---

## Monitoring

### Actuator Endpoints

| Endpoint               | Auth Required | Description            |
|------------------------|---------------|------------------------|
| `/actuator/health`     | No            | App + DB + Kafka health|
| `/actuator/info`       | No            | Build info             |
| `/actuator/metrics`    | Yes (ADMIN)   | JVM, HTTP, Kafka stats |

### Extending to Prometheus + Grafana

1. Add to `pom.xml`:
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

2. Add to `application.yml`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

3. Configure Prometheus to scrape `http://app:8080/actuator/prometheus`

4. Import Grafana dashboard ID **4701** (JVM Micrometer) for instant visibility.

---

## Project Structure

```
order-payment-system/
├── src/
│   ├── main/
│   │   ├── java/com/example/orderpayment/
│   │   │   ├── OrderPaymentApplication.java
│   │   │   ├── order/
│   │   │   │   ├── controller/OrderController.java
│   │   │   │   ├── service/OrderService.java
│   │   │   │   ├── producer/OrderProducer.java
│   │   │   │   ├── dto/{CreateOrderRequest, OrderResponse, OrderEvent}.java
│   │   │   │   ├── entity/{Order, OrderStatus}.java
│   │   │   │   └── repository/OrderRepository.java
│   │   │   ├── payment/
│   │   │   │   ├── consumer/PaymentConsumer.java
│   │   │   │   ├── service/PaymentService.java
│   │   │   │   ├── producer/PaymentProducer.java
│   │   │   │   ├── dto/PaymentEvent.java
│   │   │   │   ├── entity/{Payment, PaymentStatus}.java
│   │   │   │   └── repository/PaymentRepository.java
│   │   │   ├── notification/
│   │   │   │   ├── consumer/NotificationConsumer.java
│   │   │   │   └── service/NotificationService.java
│   │   │   └── common/
│   │   │       ├── config/{KafkaTopicConfig, KafkaConsumerConfig, OpenApiConfig}.java
│   │   │       ├── exception/{GlobalExceptionHandler, OrderNotFoundException,
│   │   │       │              InvalidOrderStateException, DuplicateOrderException}.java
│   │   │       ├── idempotency/{IdempotencyService, ProcessedEvent,
│   │   │       │                ProcessedEventId, ProcessedEventRepository}.java
│   │   │       └── security/SecurityConfig.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/V1__init.sql
│   └── test/
│       └── java/com/example/orderpayment/
│           ├── order/OrderServiceTest.java
│           ├── payment/PaymentServiceTest.java
│           └── integration/OrderPaymentIntegrationTest.java
├── Dockerfile
├── docker-compose.yml
├── Jenkinsfile
├── .env.example
├── .gitignore
└── README.md
```
