package com.example.orderpayment.payment.consumer;

import com.example.orderpayment.common.idempotency.IdempotencyService;
import com.example.orderpayment.order.dto.OrderEvent;
import com.example.orderpayment.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for order-topic.
 * Consumer group: payment-group
 *
 * Parallelism note:
 *   - order-topic has 3 partitions.
 *   - payment-group may have up to 3 consumer instances (one per partition).
 *   - Each app instance contributes up to concurrency=3 threads.
 *   - To scale beyond 3 parallel consumers, increase partition count
 *     and add more app instances (or raise concurrency).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentConsumer {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    @Value("${kafka.topics.order}")
    private String orderTopic;

    @KafkaListener(
        topics = "${kafka.topics.order}",
        groupId = "${kafka.consumer.group.payment}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderEvent(ConsumerRecord<String, OrderEvent> record) {
        OrderEvent event = record.value();
        String eventId = event.getEventId();

        log.info("[CONSUMER] Received OrderEvent from topic={}, partition={}, offset={}, key={}, eventId={}",
                record.topic(), record.partition(), record.offset(), record.key(), eventId);

        // --- Idempotency check: skip if already processed ---
        if (idempotencyService.isAlreadyProcessed(eventId, orderTopic)) {
            return; // skip is logged inside IdempotencyService
        }

        // --- Process payment ---
        paymentService.processPayment(event);

        // --- Mark event as processed AFTER successful processing ---
        idempotencyService.markAsProcessed(eventId, orderTopic);
    }
}
