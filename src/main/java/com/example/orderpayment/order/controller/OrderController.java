package com.example.orderpayment.order.controller;

import com.example.orderpayment.order.dto.CreateOrderRequest;
import com.example.orderpayment.order.dto.OrderResponse;
import com.example.orderpayment.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management endpoints")
@SecurityRequirement(name = "basicAuth")
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Create a new order",
               description = "Creates an order and publishes it to Kafka for async payment processing")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Order created successfully",
                     content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "409", description = "Duplicate order (idempotency key already used)"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("[API] POST /api/orders - customerId={}, amount={}", request.getCustomerId(), request.getAmount());
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get order by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order found",
                     content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<OrderResponse> getOrder(
            @Parameter(description = "UUID of the order", required = true)
            @PathVariable String orderId) {
        log.info("[API] GET /api/orders/{}", orderId);
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }

    @Operation(summary = "Get all orders", description = "Returns all orders. Admin only.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of orders"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        log.info("[API] GET /api/orders");
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @Operation(summary = "Get orders by customer ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Orders for customer"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<OrderResponse>> getOrdersByCustomer(
            @Parameter(description = "Customer ID", required = true)
            @PathVariable String customerId) {
        log.info("[API] GET /api/orders/customer/{}", customerId);
        return ResponseEntity.ok(orderService.getOrdersByCustomer(customerId));
    }
}
