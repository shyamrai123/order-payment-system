package com.example.orderpayment.common.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;

/**
 * Wires the global DefaultErrorHandler (with DLT + exponential backoff)
 * into the ConcurrentKafkaListenerContainerFactory used by all @KafkaListener methods.
 *
 * Concurrency = partition count (3) so one thread per partition per consumer group.
 * To scale beyond 3, increase partition count in KafkaTopicConfig and raise concurrency here.
 */
@Configuration
public class KafkaConsumerConfig {

    @Autowired
    private DefaultErrorHandler kafkaErrorHandler;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object>
    kafkaListenerContainerFactory(ConsumerFactory<String, Object> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // 3 concurrent consumers = 1 per Kafka partition
        factory.setConcurrency(3);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        // Manual offset commit for at-least-once delivery guarantee
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
        return factory;
    }
}
