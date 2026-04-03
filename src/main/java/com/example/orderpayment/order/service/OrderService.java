package com.example.orderpayment.order.service;

import com.example.orderpayment.common.exception.DuplicateOrderException;
import com.example.orderpayment.common.exception.InvalidOrderStateException;
import com.example.orderpayment.common.exception.OrderNotFoundException;
import com.example.orderpayment.order.dto.CreateOrderRequest;
import com.example.orderpayment.order.dto.OrderEvent;
import com.example.orderpayment.order.dto.OrderEventPublishRequest;
import com.example.orderpayment.order.dto.OrderResponse;
import com.example.orderpayment.order.entity.Order;
import com.example.orderpayment.order.entity.OrderStatus;
import com.example.orderpayment.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher; // ← replaced OrderProducer

    /**
     * Creates a new order:
     * 1. Validates idempotency key (prevents duplicate orders from same client)
     * 2. Persists order with PENDING status
     * 3. Publishes OrderEvent to Kafka AFTER transaction commits (via TransactionalEventListener)
     * 4. Updates order status to PAYMENT_PROCESSING
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        // --- Idempotency check ---
        if (request.getIdempotencyKey() != null) {
            orderRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .ifPresent(existing -> {
                        log.warn("[ORDER] Duplicate order request detected. idempotencyKey={}", request.getIdempotencyKey());
                        throw new DuplicateOrderException(request.getIdempotencyKey());
                    });
        }

        // --- Persist order ---
        Order order = Order.builder()
                .customerId(request.getCustomerId())
                .customerEmail(request.getCustomerEmail())
                .productName(request.getProductName())
                .quantity(request.getQuantity())
                .amount(request.getAmount())
                .status(OrderStatus.PENDING)
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        order = orderRepository.save(order);
        log.info("[ORDER] Order created. orderId={}, customerId={}, amount={}",
                order.getId(), order.getCustomerId(), order.getAmount());

        // --- Build Kafka event ---
        OrderEvent event = OrderEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .customerEmail(order.getCustomerEmail())
                .productName(order.getProductName())
                .quantity(order.getQuantity())
                .amount(order.getAmount())
                .createdAt(order.getCreatedAt())
                .build();

        // --- Queue event to publish AFTER transaction commits ---
        eventPublisher.publishEvent(new OrderEventPublishRequest(event)); // ← changed

        // --- Transition to PAYMENT_PROCESSING ---
        order.setStatus(OrderStatus.PAYMENT_PROCESSING);
        order = orderRepository.save(order);
        log.info("[ORDER] Order status updated to PAYMENT_PROCESSING. orderId={}", order.getId());

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderId) {
        Order order = findOrder(orderId);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByCustomer(String customerId) {
        return orderRepository.findByCustomerId(customerId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.toString()));

        validateStatusTransition(order.getStatus(), newStatus);

        order.setStatus(newStatus);
        orderRepository.save(order);
        log.info("[ORDER] Status transition. orderId={}, from={}, to={}",
                orderId, order.getStatus(), newStatus);
    }

    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        boolean valid = switch (current) {
            case PENDING -> next == OrderStatus.PAYMENT_PROCESSING || next == OrderStatus.CANCELLED;
            case PAYMENT_PROCESSING -> next == OrderStatus.PAYMENT_SUCCESS || next == OrderStatus.PAYMENT_FAILED;
            default -> false;
        };

        if (!valid) {
            throw new InvalidOrderStateException(
                    String.format("Cannot transition order from %s to %s", current, next));
        }
    }

    private Order findOrder(String orderId) {
        return orderRepository.findById(UUID.fromString(orderId))
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .customerEmail(order.getCustomerEmail())
                .productName(order.getProductName())
                .quantity(order.getQuantity())
                .amount(order.getAmount())
                .status(order.getStatus())
                .idempotencyKey(order.getIdempotencyKey())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}