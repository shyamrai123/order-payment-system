package com.example.orderpayment.payment.entity;

import com.example.orderpayment.common.enums.PostgreSQLEnumType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "order_id", nullable = false, columnDefinition = "uuid")
    private UUID orderId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

//    @Enumerated(EnumType.STRING)
//    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
//    @Column(name = "status")
//    private PaymentStatus status;


    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "payment_status")
    private PaymentStatus status;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "transaction_id", length = 128)
    private String transactionId;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
