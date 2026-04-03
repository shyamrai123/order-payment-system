package com.example.orderpayment.common.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Kafka infrastructure configuration.
 *
 * Topics: 3 partitions each for parallelism.
 *   - orderId is the message key → same order always lands on the same partition
 *     → preserves ordering per order across the pipeline.
 *   - With 3 partitions and 3 consumer instances, each instance owns one partition
 *     → full horizontal scaling up to partition count.
 *
 * Error Handling:
 *   - ExponentialBackOff: retries with increasing delays (1s, 2s, 4s …) up to maxRetries
 *   - After exhausting retries, message is published to <topic>.DLT for manual inspection
 */
@Slf4j
@Configuration
public class KafkaTopicConfig {

    @Value("${kafka.topics.order}")
    private String orderTopic;

    @Value("${kafka.topics.payment}")
    private String paymentTopic;

    @Value("${kafka.topics.payment-failed}")
    private String paymentFailedTopic;

    @Value("${kafka.topics.notification}")
    private String notificationTopic;

    private static final int PARTITIONS = 3;
    private static final int REPLICATION_FACTOR = 1;

    // ---- Topic Beans ----

    @Bean
    public NewTopic orderTopicBean() {
        return TopicBuilder.name(orderTopic)
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic paymentTopicBean() {
        return TopicBuilder.name(paymentTopic)
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic paymentFailedTopicBean() {
        return TopicBuilder.name(paymentFailedTopic)
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic notificationTopicBean() {
        return TopicBuilder.name(notificationTopic)
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    // DLT topics (auto-created by DeadLetterPublishingRecoverer with .DLT suffix)
    // e.g. order-topic.DLT, payment-topic.DLT

    /**
     * Global Kafka error handler with exponential backoff + Dead Letter Topic.
     *
     * Flow on consumer exception:
     *   Attempt 1 → fail → wait 1s
     *   Attempt 2 → fail → wait 2s
     *   Attempt 3 → fail → wait 4s
     *   Exhausted → publish to <topic>.DLT
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // Recoverer: publishes failed record to <original-topic>.DLT
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    log.error("[DLT] Publishing failed message to DLT. Topic={}, Key={}, Error={}",
                            record.topic(), record.key(), ex.getMessage());
                    return new TopicPartition(record.topic() + ".DLT", record.partition());
                });

        // Exponential backoff: initial 1s, multiplier 2x, max 3 retries
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxElapsedTime(10_000L); // max total wait ~10s

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("[RETRY] Attempt #{} for topic={}, key={}, error={}",
                        deliveryAttempt, record.topic(), record.key(), ex.getMessage()));

        return handler;
    }
}
