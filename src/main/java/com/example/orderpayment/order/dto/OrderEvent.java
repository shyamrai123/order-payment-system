package com.example.orderpayment.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka message payload published to order-topic.
 * eventId is a unique identifier used by consumers for idempotency.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {

    /** Unique event ID for idempotency deduplication on consumer side */
    private String eventId;

    private UUID orderId;
    private String customerId;
    private String customerEmail;
    private String productName;
    private Integer quantity;
    private BigDecimal amount;
    private LocalDateTime createdAt;
}
