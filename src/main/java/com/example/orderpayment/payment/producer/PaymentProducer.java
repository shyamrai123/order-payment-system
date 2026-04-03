package com.example.orderpayment.payment.producer;

import com.example.orderpayment.payment.dto.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes payment result events.
 * Key = orderId for consistent partition routing (same order → same partition across all topics).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.payment}")
    private String paymentTopic;

    @Value("${kafka.topics.payment-failed}")
    private String paymentFailedTopic;

    public void publishPaymentSuccess(PaymentEvent event) {
        publish(paymentTopic, event);
        log.info("[PRODUCER] Payment success event published. orderId={}, transactionId={}",
                event.getOrderId(), event.getTransactionId());
    }

    public void publishPaymentFailure(PaymentEvent event) {
        publish(paymentFailedTopic, event);
        log.warn("[PRODUCER] Payment failure event published. orderId={}, reason={}",
                event.getOrderId(), event.getFailureReason());
    }

    private void publish(String topic, PaymentEvent event) {
        String key = event.getOrderId().toString();

        log.info("[PRODUCER] Publishing PaymentEvent to topic={}, key={}, eventId={}",
                topic, key, event.getEventId());

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[PRODUCER] Failed to publish PaymentEvent. topic={}, orderId={}, error={}",
                        topic, key, ex.getMessage());
            } else {
                log.info("[PRODUCER] PaymentEvent published. topic={}, orderId={}, partition={}, offset={}",
                        topic, key,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
