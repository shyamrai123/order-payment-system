package com.example.orderpayment.notification.service;

import com.example.orderpayment.payment.dto.PaymentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Notification service simulates sending alerts (email/SMS/push).
 *
 * In production, replace log statements with:
 *   - Email via Spring Mail / AWS SES
 *   - SMS via Twilio
 *   - Push via Firebase
 *   - Webhook via HTTP client
 */
@Slf4j
@Service
public class NotificationService {

    public void sendPaymentSuccessNotification(PaymentEvent event) {
        log.info("[NOTIFICATION] ✅ Payment SUCCESS notification sent.");
        log.info("[NOTIFICATION]    To: {}", event.getCustomerEmail());
        log.info("[NOTIFICATION]    Order ID: {}", event.getOrderId());
        log.info("[NOTIFICATION]    Amount: ${}", event.getAmount());
        log.info("[NOTIFICATION]    Transaction ID: {}", event.getTransactionId());
        log.info("[NOTIFICATION]    Message: Your payment of ${} was successful! Transaction: {}",
                event.getAmount(), event.getTransactionId());

        // TODO: replace with actual email/SMS integration:
        // emailService.send(event.getCustomerEmail(), buildSuccessEmail(event));
    }

    public void sendPaymentFailureNotification(PaymentEvent event) {
        log.warn("[NOTIFICATION] ❌ Payment FAILURE notification sent.");
        log.warn("[NOTIFICATION]    To: {}", event.getCustomerEmail());
        log.warn("[NOTIFICATION]    Order ID: {}", event.getOrderId());
        log.warn("[NOTIFICATION]    Amount: ${}", event.getAmount());
        log.warn("[NOTIFICATION]    Reason: {}", event.getFailureReason());
        log.warn("[NOTIFICATION]    Message: Your payment of ${} failed. Reason: {}. Please retry.",
                event.getAmount(), event.getFailureReason());

        // TODO: replace with actual email/SMS integration:
        // emailService.send(event.getCustomerEmail(), buildFailureEmail(event));
    }
}
