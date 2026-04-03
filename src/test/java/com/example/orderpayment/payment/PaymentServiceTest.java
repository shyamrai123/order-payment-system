package com.example.orderpayment.payment;

import com.example.orderpayment.order.dto.OrderEvent;
import com.example.orderpayment.order.entity.Order;
import com.example.orderpayment.order.entity.OrderStatus;
import com.example.orderpayment.order.repository.OrderRepository;
import com.example.orderpayment.payment.dto.PaymentEvent;
import com.example.orderpayment.payment.entity.Payment;
import com.example.orderpayment.payment.entity.PaymentStatus;
import com.example.orderpayment.payment.producer.PaymentProducer;
import com.example.orderpayment.payment.repository.PaymentRepository;
import com.example.orderpayment.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentProducer paymentProducer;

    @InjectMocks
    private PaymentService paymentService;

    private OrderEvent orderEvent;
    private Order order;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();

        orderEvent = OrderEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId(orderId)
                .customerId("CUST-001")
                .customerEmail("test@example.com")
                .productName("Laptop")
                .quantity(1)
                .amount(new BigDecimal("999.99"))
                .createdAt(LocalDateTime.now())
                .build();

        order = Order.builder()
                .id(orderId)
                .customerId("CUST-001")
                .customerEmail("test@example.com")
                .productName("Laptop")
                .quantity(1)
                .amount(new BigDecimal("999.99"))
                .status(OrderStatus.PAYMENT_PROCESSING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("processPayment - should save payment record initially as INITIATED")
    void processPayment_savesInitialPaymentRecord() {
        Payment initiatedPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .amount(new BigDecimal("999.99"))
                .status(PaymentStatus.INITIATED)
                .build();

        when(paymentRepository.save(any(Payment.class))).thenReturn(initiatedPayment);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        paymentService.processPayment(orderEvent);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeast(1)).save(paymentCaptor.capture());
        Payment firstSave = paymentCaptor.getAllValues().get(0);
        assertThat(firstSave.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(firstSave.getAmount()).isEqualByComparingTo("999.99");
        assertThat(firstSave.getOrderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("processPayment - should always publish exactly one payment event (success or failure)")
    void processPayment_alwaysPublishesOneEvent() {
        Payment initiatedPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .amount(new BigDecimal("999.99"))
                .status(PaymentStatus.INITIATED)
                .build();

        when(paymentRepository.save(any(Payment.class))).thenReturn(initiatedPayment);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        doNothing().when(paymentProducer).publishPaymentSuccess(any());
        doNothing().when(paymentProducer).publishPaymentFailure(any());

        paymentService.processPayment(orderEvent);

        int successCalls = mockingDetails(paymentProducer).getInvocations()
                .stream().filter(i -> i.getMethod().getName().equals("publishPaymentSuccess"))
                .mapToInt(i -> 1).sum();
        int failureCalls = mockingDetails(paymentProducer).getInvocations()
                .stream().filter(i -> i.getMethod().getName().equals("publishPaymentFailure"))
                .mapToInt(i -> 1).sum();

        assertThat(successCalls + failureCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("processPayment - order status should be updated on payment outcome")
    void processPayment_updatesOrderStatus() {
        Payment savedPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .amount(new BigDecimal("999.99"))
                .status(PaymentStatus.INITIATED)
                .build();

        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        doNothing().when(paymentProducer).publishPaymentSuccess(any());
        doNothing().when(paymentProducer).publishPaymentFailure(any());

        paymentService.processPayment(orderEvent);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        Order updatedOrder = orderCaptor.getValue();
        assertThat(updatedOrder.getStatus())
                .isIn(OrderStatus.PAYMENT_SUCCESS, OrderStatus.PAYMENT_FAILED);
    }

    @RepeatedTest(10)
    @DisplayName("processPayment - over 10 runs, both outcomes should occur statistically")
    void processPayment_statisticalOutcomes() {
        Payment savedPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .amount(new BigDecimal("999.99"))
                .status(PaymentStatus.INITIATED)
                .build();

        when(paymentRepository.save(any())).thenReturn(savedPayment);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);
        doNothing().when(paymentProducer).publishPaymentSuccess(any());
        doNothing().when(paymentProducer).publishPaymentFailure(any());

        paymentService.processPayment(orderEvent);
    }
}