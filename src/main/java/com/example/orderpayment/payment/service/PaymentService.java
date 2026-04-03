package com.example.orderpayment.payment.service;

import com.example.orderpayment.order.dto.OrderEvent;
import com.example.orderpayment.order.entity.OrderStatus;
import com.example.orderpayment.order.repository.OrderRepository;
import com.example.orderpayment.payment.dto.PaymentEvent;
import com.example.orderpayment.payment.entity.Payment;
import com.example.orderpayment.payment.entity.PaymentStatus;
import com.example.orderpayment.payment.producer.PaymentProducer;
import com.example.orderpayment.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

/**
 * Core payment processing logic.
 *
 * Simulates a payment gateway:
 *   - 80% probability → SUCCESS → publishes to payment-topic
 *   - 20% probability → FAILURE → publishes to payment-failed-topic
 *
 * In production, replace the random logic with actual gateway integration
 * (Stripe, Razorpay, etc.).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentProducer paymentProducer;

    private static final Random RANDOM = new Random();
    private static final double SUCCESS_RATE = 0.80;

    @Transactional
    public void processPayment(OrderEvent orderEvent) {
        log.info("[PAYMENT] Processing payment for orderId={}, amount={}",
                orderEvent.getOrderId(), orderEvent.getAmount());

        // --- Create initial payment record ---
        Payment payment = Payment.builder()
                .orderId(orderEvent.getOrderId())
                .amount(orderEvent.getAmount())
                .status(PaymentStatus.INITIATED)
                .build();
        payment = paymentRepository.save(payment);

        // --- Simulate payment gateway call ---
        boolean isSuccess = RANDOM.nextDouble() < SUCCESS_RATE;

        PaymentEvent paymentEvent;

        if (isSuccess) {
            String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setTransactionId(transactionId);
            payment.setProcessedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            // Update order status
            updateOrderStatus(orderEvent.getOrderId(), OrderStatus.PAYMENT_SUCCESS);

            paymentEvent = buildPaymentEvent(orderEvent, payment, "SUCCESS", null, transactionId);
            paymentProducer.publishPaymentSuccess(paymentEvent);

            log.info("[PAYMENT] Payment SUCCESS. orderId={}, paymentId={}, transactionId={}",
                    orderEvent.getOrderId(), payment.getId(), transactionId);

        } else {
            String failureReason = pickFailureReason();

            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(failureReason);
            payment.setProcessedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            // Update order status
            updateOrderStatus(orderEvent.getOrderId(), OrderStatus.PAYMENT_FAILED);

            paymentEvent = buildPaymentEvent(orderEvent, payment, "FAILED", failureReason, null);
            paymentProducer.publishPaymentFailure(paymentEvent);

            log.warn("[PAYMENT] Payment FAILED. orderId={}, paymentId={}, reason={}",
                    orderEvent.getOrderId(), payment.getId(), failureReason);
        }
    }

    private void updateOrderStatus(UUID orderId, OrderStatus status) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(status);
            orderRepository.save(order);
            log.info("[PAYMENT] Updated order status. orderId={}, status={}", orderId, status);
        });
    }

    private PaymentEvent buildPaymentEvent(OrderEvent orderEvent, Payment payment,
                                           String status, String failureReason, String transactionId) {
        return PaymentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .paymentId(payment.getId())
                .orderId(orderEvent.getOrderId())
                .customerId(orderEvent.getCustomerId())
                .customerEmail(orderEvent.getCustomerEmail())
                .amount(orderEvent.getAmount())
                .status(payment.getStatus())
                .failureReason(failureReason)
                .transactionId(transactionId)
                .processedAt(payment.getProcessedAt())
                .build();
    }

    private String pickFailureReason() {
        String[] reasons = {
            "Insufficient funds",
            "Card declined by issuer",
            "Payment gateway timeout",
            "Invalid card number",
            "Transaction limit exceeded"
        };
        return reasons[RANDOM.nextInt(reasons.length)];
    }
}
