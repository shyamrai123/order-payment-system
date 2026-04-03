package com.example.orderpayment.payment.dto;

import com.example.orderpayment.payment.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka message payload published to payment-topic (success) or payment-failed-topic (failure).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {

    /** Unique event ID for consumer-side idempotency */
    private String eventId;

    private UUID paymentId;
    private UUID orderId;
    private String customerId;
    private String customerEmail;
    private BigDecimal amount;
    private PaymentStatus status;         //"SUCCESS" or "FAILED"
    private String failureReason;    // populated only on failure
    private String transactionId;    // populated only on success
    private LocalDateTime processedAt;
}
