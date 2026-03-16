# AWS SQS Order Processing — Spring Boot + AWS Lambda

A complete **E-commerce Order Processing** system built with Spring Boot 3, AWS SQS, and AWS Lambda. Demonstrates a real-world async message queue pattern where orders are placed via REST API, queued in SQS, and processed by a background worker.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Project Structure](#project-structure)
- [Complete Flow](#complete-flow)
  - [Step 1 — Place an Order](#step-1--place-an-order)
  - [Step 2 — Push to SQS](#step-2--push-to-sqs)
  - [Step 3 — Worker Polls SQS](#step-3--worker-polls-sqs)
  - [Step 4 — Process the Order](#step-4--process-the-order)
  - [Step 5 — Delete from SQS](#step-5--delete-from-sqs)
  - [Step 6 — Check Order Status](#step-6--check-order-status)
- [API Endpoints](#api-endpoints)
- [File Reference Guide](#file-reference-guide)
- [Setup & Configuration](#setup--configuration)
- [Running Locally](#running-locally)
- [Production — Lambda Deployment](#production--lambda-deployment)
- [Order Status Lifecycle](#order-status-lifecycle)
- [Why SQS? — Key Benefits](#why-sqs--key-benefits)
- [Tech Stack](#tech-stack)

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│                   CLIENT (Postman / App)                     │
└─────────────────────────┬────────────────────────────────────┘
                          │  POST /order/place
                          ▼
┌──────────────────────────────────────────────────────────────┐
│                    OrderController                           │
│            REST entry point — receives HTTP request          │
└─────────────────────────┬────────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────────────┐
│                     OrderService                             │
│  Creates Order (PENDING) → stores in memory → sends to SQS   │
└─────────────────────────┬────────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────────────┐
│                      SqsService                              │
│       sendMessage() / receiveMessages() / deleteMessage()    │
└─────────────────────────┬────────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────────────┐
│                    AWS SQS Queue                             │
│           Messages wait here until consumed                  │
└─────────────────────────┬────────────────────────────────────┘
                          │  polled every 5 seconds
                          ▼
┌──────────────────────────────────────────────────────────────┐
│                     OrderWorker                              │
│      @Scheduled — pulls messages → calls OrderService        │
└─────────────────────────┬────────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────────────┐
│                     OrderService                             │
│  PENDING → PROCESSING → COMPLETED → deleteMessage from SQS   │
└──────────────────────────────────────────────────────────────┘
```

---

## Project Structure

```
src/main/java/org/example/
│
├── Application.java                   ← Spring Boot entry point + @EnableScheduling
├── StreamLambdaHandler.java           ← AWS Lambda handler (for production deploy)
│
├── config/
│   └── AwsConfig.java                 ← Builds SqsClient with credentials
│
├── controller/
│   ├── OrderController.java           ← Order REST endpoints (/order/*)
│   ├── SqsController.java             ← Raw SQS endpoints (/sqs/*)
│   └── PingController.java            ← Health check (/ping)
│
├── service/
│   ├── OrderService.java              ← Business logic + in-memory order store
│   └── SqsService.java                ← AWS SQS operations (send/receive/delete)
│
├── worker/
│   └── OrderWorker.java               ← Background poller (@Scheduled every 5s)
│
├── model/
│   ├── Order.java                     ← Order entity (orderId, product, status...)
│   └── OrderStatus.java               ← Enum: PENDING / PROCESSING / COMPLETED / FAILED
│
└── dto/
    ├── OrderRequest.java              ← Request body for placing an order
    └── ApiResponse.java               ← Standard JSON response wrapper
```

---

## Complete Flow

### Step 1 — Place an Order

**File:** [`controller/OrderController.java`](src/main/java/org/example/controller/OrderController.java)

The client sends a `POST` request. The controller validates the request and delegates to `OrderService`.

```java
@PostMapping("/place")
public ResponseEntity<ApiResponse<Order>> placeOrder(@RequestBody OrderRequest request) {
    Order order = orderService.placeOrder(request);
    return ResponseEntity.ok(ApiResponse.ok("Order placed successfully", order));
}
```

**Request body is defined in:** [`dto/OrderRequest.java`](src/main/java/org/example/dto/OrderRequest.java)

```json
{
  "userId": "user-001",
  "product": "iPhone 15",
  "quantity": 2
}
```

**Response is wrapped by:** [`dto/ApiResponse.java`](src/main/java/org/example/dto/ApiResponse.java)

```json
{
  "success": true,
  "message": "Order placed successfully",
  "data": {
    "orderId": "abc-123",
    "status": "PENDING",
    "product": "iPhone 15",
    "quantity": 2,
    "createdAt": "2024-01-15T10:30:00Z"
  }
}
```

---

### Step 2 — Push to SQS

**File:** [`service/OrderService.java`](src/main/java/org/example/service/OrderService.java)

`OrderService` creates the `Order` object, saves it in the in-memory store, serializes it to JSON, and pushes it to the SQS queue.

```java
public Order placeOrder(OrderRequest request) {
    String orderId = UUID.randomUUID().toString();

    // 1. Create order with PENDING status
    Order order = new Order(orderId, request.getUserId(), request.getProduct(), request.getQuantity());

    // 2. Save in memory (acts as our database)
    orderStore.put(orderId, order);

    // 3. Serialize and push to SQS
    String messageBody = objectMapper.writeValueAsString(Map.of(
            "orderId", orderId,
            "userId", request.getUserId(),
            "product", request.getProduct(),
            "quantity", request.getQuantity()
    ));
    sqsService.sendMessage(messageBody);

    return order;
}
```

**Order model is defined in:** [`model/Order.java`](src/main/java/org/example/model/Order.java)
**Status values are defined in:** [`model/OrderStatus.java`](src/main/java/org/example/model/OrderStatus.java)

The actual AWS SQS call happens in:

**File:** [`service/SqsService.java`](src/main/java/org/example/service/SqsService.java)

```java
public String sendMessage(String message) {
    SendMessageRequest request = SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(message)
            .build();
    return sqsClient.sendMessage(request).messageId();
}
```

**Queue URL is configured in:** [`resources/application.properties`](src/main/resources/application.properties)
**SqsClient is built in:** [`config/AwsConfig.java`](src/main/java/org/example/config/AwsConfig.java)

---

### Step 3 — Worker Polls SQS

**File:** [`worker/OrderWorker.java`](src/main/java/org/example/worker/OrderWorker.java)

Every 5 seconds, the worker wakes up and asks SQS for pending messages. If messages exist, it hands each one to `OrderService` for processing.

```java
@Scheduled(fixedDelay = 5000)
public void pollAndProcess() {
    List<Message> messages = sqsService.receiveMessages(5);  // fetch up to 5 messages

    if (messages.isEmpty()) return;

    for (Message message : messages) {
        orderService.processOrder(message.body(), message.receiptHandle());
    }
}
```

> `@Scheduled` is enabled by `@EnableScheduling` in [`Application.java`](src/main/java/org/example/Application.java)

The receive call in **[`service/SqsService.java`](src/main/java/org/example/service/SqsService.java)**:

```java
public List<Message> receiveMessages(int maxMessages) {
    ReceiveMessageRequest request = ReceiveMessageRequest.builder()
            .queueUrl(queueUrl)
            .maxNumberOfMessages(maxMessages)
            .waitTimeSeconds(10)   // long polling — waits up to 10s for messages
            .build();
    return sqsClient.receiveMessage(request).messages();
}
```

> SQS does **not** delete the message here — it only hides it (visibility timeout ~30s).
> The message is permanently deleted only after successful processing in Step 5.

---

### Step 4 — Process the Order

**File:** [`service/OrderService.java`](src/main/java/org/example/service/OrderService.java)

This is the core business logic. The worker passes the raw SQS message body and receipt handle here.

```java
public void processOrder(String messageBody, String receiptHandle) {

    // 1. Parse JSON → extract orderId
    Map<?, ?> data = objectMapper.readValue(messageBody, Map.class);
    String orderId = (String) data.get("orderId");

    // 2. Find the order in memory
    Order order = orderStore.get(orderId);

    // 3. Mark as PROCESSING
    order.setStatus(OrderStatus.PROCESSING);

    // 4. Do the real work here (in production):
    //    - Charge card via Stripe
    //    - Deduct from inventory DB
    //    - Send confirmation email via SES
    //    - Notify shipping service
    Thread.sleep(500);  // simulating work

    // 5. Mark as COMPLETED
    order.setStatus(OrderStatus.COMPLETED);

    // 6. Delete from SQS — only after success
    sqsService.deleteMessage(receiptHandle);
}
```

**Status transitions use:** [`model/OrderStatus.java`](src/main/java/org/example/model/OrderStatus.java)
**Status is updated via:** [`model/Order.java`](src/main/java/org/example/model/Order.java) → `setStatus()`

> **Failure handling:** If any exception is thrown, `deleteMessage()` is **not** called.
> SQS automatically makes the message visible again after the visibility timeout (default 30s), triggering an automatic retry.

---

### Step 5 — Delete from SQS

**File:** [`service/SqsService.java`](src/main/java/org/example/service/SqsService.java)

Once processing succeeds, the message is permanently removed from the queue using the `receiptHandle`.

```java
public void deleteMessage(String receiptHandle) {
    DeleteMessageRequest request = DeleteMessageRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiptHandle)
            .build();
    sqsClient.deleteMessage(request);
}
```

> **`receiptHandle` vs `messageId`**
> | Field | What it is |
> |---|---|
> | `messageId` | Permanent ID of the message (never changes) |
> | `receiptHandle` | Temporary token for THIS specific receive — used to delete |
>
> `receiptHandle` changes every time you receive the same message. Always use it for deletion.

---

### Step 6 — Check Order Status

**File:** [`controller/OrderController.java`](src/main/java/org/example/controller/OrderController.java)

The client polls this endpoint to see the current status of their order.

```java
@GetMapping("/{orderId}/status")
public ResponseEntity<ApiResponse<Order>> getOrderStatus(@PathVariable String orderId) {
    return orderService.getOrder(orderId)
            .map(order -> ResponseEntity.ok(ApiResponse.ok("Order found", order)))
            .orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.error("Order not found: " + orderId)));
}
```

**Lookup is done in:** [`service/OrderService.java`](src/main/java/org/example/service/OrderService.java) → `getOrder()`

```json
{
  "success": true,
  "message": "Order found",
  "data": {
    "orderId": "abc-123",
    "status": "COMPLETED",
    "product": "iPhone 15",
    "quantity": 2,
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:06Z"
  }
}
```

---

## API Endpoints

### Order Endpoints

| Method | Endpoint | Description | File |
|--------|----------|-------------|------|
| `POST` | `/order/place` | Place a new order | [`OrderController.java`](src/main/java/org/example/controller/OrderController.java) |
| `GET` | `/order/{orderId}/status` | Get order status | [`OrderController.java`](src/main/java/org/example/controller/OrderController.java) |
| `GET` | `/order/all` | List all orders | [`OrderController.java`](src/main/java/org/example/controller/OrderController.java) |

### Raw SQS Endpoints

| Method | Endpoint | Description | File |
|--------|----------|-------------|------|
| `POST` | `/sqs/send` | Send raw message to SQS | [`SqsController.java`](src/main/java/org/example/controller/SqsController.java) |
| `GET` | `/sqs/receive` | Receive messages from SQS | [`SqsController.java`](src/main/java/org/example/controller/SqsController.java) |
| `GET` | `/sqs/receive/{messageId}` | Find message by ID | [`SqsController.java`](src/main/java/org/example/controller/SqsController.java) |

### Health Check

| Method | Endpoint | Description | File |
|--------|----------|-------------|------|
| `GET` | `/ping` | Health check | [`PingController.java`](src/main/java/org/example/controller/PingController.java) |

---

## File Reference Guide

| File | Role | Talks To |
|------|------|----------|
| [`Application.java`](src/main/java/org/example/Application.java) | App entry point, enables scheduling, imports all beans | — |
| [`StreamLambdaHandler.java`](src/main/java/org/example/StreamLambdaHandler.java) | AWS Lambda adapter wrapping Spring Boot | [`Application.java`](src/main/java/org/example/Application.java) |
| [`AwsConfig.java`](src/main/java/org/example/config/AwsConfig.java) | Builds `SqsClient` with credentials | [`application.properties`](src/main/resources/application.properties) |
| [`OrderController.java`](src/main/java/org/example/controller/OrderController.java) | HTTP layer — handles `/order/*` routes | [`OrderService.java`](src/main/java/org/example/service/OrderService.java) |
| [`SqsController.java`](src/main/java/org/example/controller/SqsController.java) | HTTP layer — handles `/sqs/*` routes | [`SqsService.java`](src/main/java/org/example/service/SqsService.java) |
| [`PingController.java`](src/main/java/org/example/controller/PingController.java) | Health check endpoint | — |
| [`OrderService.java`](src/main/java/org/example/service/OrderService.java) | Business logic + in-memory order store | [`SqsService.java`](src/main/java/org/example/service/SqsService.java), [`Order.java`](src/main/java/org/example/model/Order.java) |
| [`SqsService.java`](src/main/java/org/example/service/SqsService.java) | AWS SQS operations — send, receive, delete | AWS SQS SDK |
| [`OrderWorker.java`](src/main/java/org/example/worker/OrderWorker.java) | Background scheduler, polls SQS every 5s | [`SqsService.java`](src/main/java/org/example/service/SqsService.java), [`OrderService.java`](src/main/java/org/example/service/OrderService.java) |
| [`Order.java`](src/main/java/org/example/model/Order.java) | Order entity with status tracking | [`OrderStatus.java`](src/main/java/org/example/model/OrderStatus.java) |
| [`OrderStatus.java`](src/main/java/org/example/model/OrderStatus.java) | Enum: `PENDING` `PROCESSING` `COMPLETED` `FAILED` | — |
| [`OrderRequest.java`](src/main/java/org/example/dto/OrderRequest.java) | Incoming request DTO | — |
| [`ApiResponse.java`](src/main/java/org/example/dto/ApiResponse.java) | Standard response wrapper (`success`, `message`, `data`, `totalMessages`) | — |

---

## Setup & Configuration

### Prerequisites

- Java 17+
- Maven 3.8+
- AWS Account with an SQS Queue created
- AWS IAM user with the following permissions:
  - `sqs:SendMessage`
  - `sqs:ReceiveMessage`
  - `sqs:DeleteMessage`

### Configuration

Create `src/main/resources/application.properties` (already gitignored for security):

```properties
# AWS Credentials
aws.accessKeyId=YOUR_ACCESS_KEY_ID
aws.secretAccessKey=YOUR_SECRET_ACCESS_KEY
aws.region=eu-central-1

# SQS Queue URL (from AWS Console → SQS → your queue)
aws.sqs.queue-url=https://sqs.eu-central-1.amazonaws.com/YOUR_ACCOUNT_ID/YOUR_QUEUE_NAME

# Spring Boot
logging.level.org.example=INFO
spring.main.lazy-initialization=true
spring.jmx.enabled=false
spring.main.banner-mode=off

# Hot reload (dev only)
spring.devtools.restart.enabled=true
```

> Credentials are read by [`AwsConfig.java`](src/main/java/org/example/config/AwsConfig.java).
> **Never commit credentials to Git.** Use environment variables or AWS IAM roles in production.

---

## Running Locally

```bash
# 1. Clone the repo
git clone <your-repo-url>
cd aws-sdk

# 2. Add your application.properties (see Setup above)

# 3. Build
mvn clean install

# 4. Run
mvn spring-boot:run
```

### Test the Full Flow

```bash
# 1. Place an order
curl -X POST http://localhost:8080/order/place \
  -H "Content-Type: application/json" \
  -d '{"userId":"user-001","product":"iPhone 15","quantity":2}'

# Response:
# { "success": true, "data": { "orderId": "abc-123", "status": "PENDING" } }


# 2. Check status immediately (will be PENDING)
curl http://localhost:8080/order/abc-123/status


# 3. Wait 5 seconds for OrderWorker to pick it up, then check again
curl http://localhost:8080/order/abc-123/status
# { "data": { "status": "COMPLETED" } }


# 4. View all orders
curl http://localhost:8080/order/all


# 5. Health check
curl http://localhost:8080/ping
```

---

## Production — Lambda Deployment

This project is pre-configured for AWS Lambda via [`StreamLambdaHandler.java`](src/main/java/org/example/StreamLambdaHandler.java).

In production, replace the polling worker with a **Lambda + SQS Trigger**:

```
SQS Queue ──► AWS invokes Lambda automatically ──► processes message ──► deletes it
```

**Benefits over polling:**

| | Polling (current) | Lambda Trigger (production) |
|---|---|---|
| Cost | Pays for empty polls | Pays only when messages exist |
| Latency | Up to 5s delay | Near real-time |
| Scale | Manual (one server) | Auto-scales to 1000s of messages |
| Maintenance | Server must stay running | Fully serverless |

### Deploy Steps

```bash
# 1. Package for Lambda
mvn package -P shaded-jar

# 2. Upload target/aws-sdk-1.0-SNAPSHOT.jar to AWS Lambda

# 3. Set handler to:
#    org.example.StreamLambdaHandler::handleRequest

# 4. In AWS Console → Lambda → your function → Add Trigger → SQS
#    Select your queue → Save
```

Once the SQS trigger is set, AWS will automatically invoke your Lambda whenever a message arrives — no polling needed.

---

## Order Status Lifecycle

```
POST /order/place
        │
        ▼
   [ PENDING ]  ── order created, sitting in SQS queue
        │
        │  OrderWorker picks up message (every 5s)
        ▼
 [ PROCESSING ] ── payment, inventory, email being processed
        │
   ┌────┴────┐
   │         │
   ▼         ▼
[COMPLETED] [FAILED] ── exception thrown during processing
                         message stays in SQS → retried after 30s
```

| Status | Meaning | Set in |
|--------|---------|--------|
| `PENDING` | Order received, waiting in SQS | [`OrderService.java`](src/main/java/org/example/service/OrderService.java) |
| `PROCESSING` | Worker actively processing | [`OrderService.java`](src/main/java/org/example/service/OrderService.java) |
| `COMPLETED` | Successfully processed, deleted from SQS | [`OrderService.java`](src/main/java/org/example/service/OrderService.java) |
| `FAILED` | Error during SQS send at placement time | [`OrderService.java`](src/main/java/org/example/service/OrderService.java) |

All status values defined in: [`model/OrderStatus.java`](src/main/java/org/example/model/OrderStatus.java)

---

## Why SQS? — Key Benefits

| Problem | Without SQS | With SQS |
|---------|-------------|----------|
| Traffic spike | API overloads, requests dropped | Queue absorbs burst, workers process at their own pace |
| Server crash | Order lost forever | Message stays in queue, retried automatically |
| Slow processing | User waits for payment + email + stock update | User gets instant response, work happens in background |
| Scale | One server handles everything | Spin up more workers as queue grows |
| Service coupling | Order service directly calls shipping service | Each service reads from queue independently |

---

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 17 | Language |
| Spring Boot | 3.4.5 | Web framework |
| AWS SDK v2 | 2.25.60 | SQS client |
| AWS Serverless Java Container | 2.1.5 | Lambda adapter |
| Lombok | latest | Reduce boilerplate |
| Maven | 3.8+ | Build tool |
