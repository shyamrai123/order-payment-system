package com.example.orderpayment.order.dto;

import com.example.orderpayment.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Order response payload")
public class OrderResponse {

    @Schema(description = "Unique order ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Customer ID", example = "CUST-001")
    private String customerId;

    @Schema(description = "Customer email", example = "john.doe@example.com")
    private String customerEmail;

    @Schema(description = "Product name", example = "Wireless Headphones")
    private String productName;

    @Schema(description = "Quantity", example = "2")
    private Integer quantity;

    @Schema(description = "Total amount", example = "199.99")
    private BigDecimal amount;

    @Schema(description = "Current order status", example = "PAYMENT_PROCESSING")
    private OrderStatus status;

    @Schema(description = "Idempotency key provided by client")
    private String idempotencyKey;

    @Schema(description = "Order creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
}
