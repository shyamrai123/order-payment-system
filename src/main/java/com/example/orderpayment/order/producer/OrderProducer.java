package com.example.orderpayment.order.producer;

import com.example.orderpayment.order.dto.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes OrderEvent to order-topic.
 *
 * Message key = orderId (UUID as String).
 * Using orderId as key guarantees all events for a given order
 * always land on the same partition → preserves per-order ordering.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.order}")
    private String orderTopic;

    public void publishOrderEvent(OrderEvent event) {
        String key = event.getOrderId().toString();

        log.info("[PRODUCER] Publishing OrderEvent to topic={}, key={}, eventId={}",
                orderTopic, key, event.getEventId());

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(orderTopic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[PRODUCER] Failed to publish OrderEvent. orderId={}, error={}",
                        key, ex.getMessage());
            } else {
                log.info("[PRODUCER] OrderEvent published successfully. orderId={}, partition={}, offset={}",
                        key,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
