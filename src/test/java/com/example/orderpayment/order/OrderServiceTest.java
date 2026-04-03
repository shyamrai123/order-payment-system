package com.example.orderpayment.order;

import com.example.orderpayment.common.exception.DuplicateOrderException;
import com.example.orderpayment.common.exception.InvalidOrderStateException;
import com.example.orderpayment.common.exception.OrderNotFoundException;
import com.example.orderpayment.order.dto.CreateOrderRequest;
import com.example.orderpayment.order.dto.OrderResponse;
import com.example.orderpayment.order.entity.Order;
import com.example.orderpayment.order.entity.OrderStatus;
import com.example.orderpayment.order.producer.OrderProducer;
import com.example.orderpayment.order.repository.OrderRepository;
import com.example.orderpayment.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Unit Tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderProducer orderProducer;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderService orderService;

    private CreateOrderRequest validRequest;
    private Order savedOrder;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();

        ReflectionTestUtils.setField(orderService, "eventPublisher", eventPublisher);

        validRequest = new CreateOrderRequest();
        validRequest.setCustomerId("CUST-001");
        validRequest.setCustomerEmail("test@example.com");
        validRequest.setProductName("Wireless Headphones");
        validRequest.setQuantity(2);
        validRequest.setAmount(new BigDecimal("199.99"));
        validRequest.setIdempotencyKey("key-001");

        savedOrder = Order.builder()
                .id(orderId)
                .customerId("CUST-001")
                .customerEmail("test@example.com")
                .productName("Wireless Headphones")
                .quantity(2)
                .amount(new BigDecimal("199.99"))
                .status(OrderStatus.PENDING)
                .idempotencyKey("key-001")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("createOrder - should save order, publish event, and return PAYMENT_PROCESSING status")
    void createOrder_success() {
        when(orderRepository.findByIdempotencyKey("key-001")).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        doNothing().when(orderProducer).publishOrderEvent(any());

        OrderResponse response = orderService.createOrder(validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getCustomerId()).isEqualTo("CUST-001");
        assertThat(response.getAmount()).isEqualByComparingTo("199.99");

        verify(orderRepository, atLeastOnce()).save(any(Order.class));
        verify(orderProducer, times(1)).publishOrderEvent(any());
    }

    @Test
    @DisplayName("createOrder - should throw DuplicateOrderException if idempotency key exists")
    void createOrder_duplicateIdempotencyKey_throwsException() {
        when(orderRepository.findByIdempotencyKey("key-001")).thenReturn(Optional.of(savedOrder));

        assertThatThrownBy(() -> orderService.createOrder(validRequest))
                .isInstanceOf(DuplicateOrderException.class)
                .hasMessageContaining("key-001");

        verify(orderRepository, never()).save(any());
        verify(orderProducer, never()).publishOrderEvent(any());
    }

    @Test
    @DisplayName("createOrder - should work without idempotency key")
    void createOrder_withoutIdempotencyKey_success() {
        validRequest.setIdempotencyKey(null);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        doNothing().when(orderProducer).publishOrderEvent(any());

        OrderResponse response = orderService.createOrder(validRequest);

        assertThat(response).isNotNull();
        verify(orderRepository, never()).findByIdempotencyKey(any());
    }

    @Test
    @DisplayName("getOrder - should return order when found")
    void getOrder_found() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(savedOrder));

        OrderResponse response = orderService.getOrder(orderId.toString());

        assertThat(response.getId()).isEqualTo(orderId);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("getOrder - should throw OrderNotFoundException when not found")
    void getOrder_notFound() {
        when(orderRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(orderId.toString()))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }

    @Test
    @DisplayName("updateOrderStatus - valid transition PAYMENT_PROCESSING to PAYMENT_SUCCESS")
    void updateOrderStatus_validTransition() {
        savedOrder.setStatus(OrderStatus.PAYMENT_PROCESSING);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(savedOrder));
        when(orderRepository.save(any())).thenReturn(savedOrder);

        assertThatCode(() -> orderService.updateOrderStatus(orderId, OrderStatus.PAYMENT_SUCCESS))
                .doesNotThrowAnyException();

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.PAYMENT_SUCCESS);
    }

    @Test
    @DisplayName("updateOrderStatus - invalid transition from terminal state throws exception")
    void updateOrderStatus_invalidTransition_throwsException() {
        savedOrder.setStatus(OrderStatus.PAYMENT_SUCCESS);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(savedOrder));

        assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, OrderStatus.PAYMENT_PROCESSING))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("PAYMENT_SUCCESS");
    }

    @Test
    @DisplayName("updateOrderStatus - invalid transition PENDING to PAYMENT_SUCCESS throws exception")
    void updateOrderStatus_pendingToSuccess_throwsException() {
        savedOrder.setStatus(OrderStatus.PENDING);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(savedOrder));

        assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, OrderStatus.PAYMENT_SUCCESS))
                .isInstanceOf(InvalidOrderStateException.class);
    }
}