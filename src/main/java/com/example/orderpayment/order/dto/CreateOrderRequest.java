package com.example.orderpayment.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Request payload to create a new order")
public class CreateOrderRequest {

    @NotBlank(message = "Customer ID is required")
    @Size(max = 64, message = "Customer ID must not exceed 64 characters")
    @Schema(description = "Unique customer identifier", example = "CUST-001")
    private String customerId;

    @NotBlank(message = "Customer email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "Customer's email address", example = "john.doe@example.com")
    private String customerEmail;

    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must not exceed 255 characters")
    @Schema(description = "Name of the product ordered", example = "Wireless Headphones")
    private String productName;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 1000, message = "Quantity must not exceed 1000")
    @Schema(description = "Number of items ordered", example = "2")
    private Integer quantity;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 13, fraction = 2, message = "Invalid amount format")
    @Schema(description = "Total order amount in USD", example = "199.99")
    private BigDecimal amount;

    @Schema(description = "Client-provided idempotency key to prevent duplicate orders",
            example = "order-req-550e8400-e29b-41d4-a716-446655440000")
    private String idempotencyKey;
}
