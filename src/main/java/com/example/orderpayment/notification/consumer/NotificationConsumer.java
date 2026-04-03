package com.example.orderpayment.notification.consumer;

import com.example.orderpayment.common.idempotency.IdempotencyService;
import com.example.orderpayment.notification.service.NotificationService;
import com.example.orderpayment.payment.dto.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumers for payment result notifications.
 * Consumer group: notification-group
 *
 * Two listeners:
 *   1. paymentSuccessConsumer → payment-topic → success alerts
 *   2. paymentFailureConsumer → payment-failed-topic → failure alerts
 *
 * Both use idempotency guard to safely handle Kafka redeliveries.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;

    @Value("${kafka.topics.payment}")
    private String paymentTopic;

    @Value("${kafka.topics.payment-failed}")
    private String paymentFailedTopic;

    @KafkaListener(
        topics = "${kafka.topics.payment}",
        groupId = "${kafka.consumer.group.notification}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentSuccess(ConsumerRecord<String, PaymentEvent> record) {
        PaymentEvent event = record.value();
        String eventId = event.getEventId();

        log.info("[CONSUMER] Received PaymentSuccess event. topic={}, partition={}, offset={}, orderId={}",
                record.topic(), record.partition(), record.offset(), event.getOrderId());

        if (idempotencyService.isAlreadyProcessed(eventId, paymentTopic)) {
            return;
        }

        notificationService.sendPaymentSuccessNotification(event);
        idempotencyService.markAsProcessed(eventId, paymentTopic);
    }

    @KafkaListener(
        topics = "${kafka.topics.payment-failed}",
        groupId = "${kafka.consumer.group.notification}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentFailure(ConsumerRecord<String, PaymentEvent> record) {
        PaymentEvent event = record.value();
        String eventId = event.getEventId();

        log.warn("[CONSUMER] Received PaymentFailure event. topic={}, partition={}, offset={}, orderId={}",
                record.topic(), record.partition(), record.offset(), event.getOrderId());

        if (idempotencyService.isAlreadyProcessed(eventId, paymentFailedTopic)) {
            return;
        }

        notificationService.sendPaymentFailureNotification(event);
        idempotencyService.markAsProcessed(eventId, paymentFailedTopic);
    }
}
