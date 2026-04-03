package com.example.orderpayment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Order Payment System.`
 *
 * Architecture:
 *   Client → POST /api/orders
 *          → Kafka: order-topic (3 partitions, keyed by orderId)
 *          → Payment Consumer (payment-group, up to 3 parallel threads per partition)
 *              → 80% success → payment-topic
 *              → 20% failure → payment-failed-topic
 *          → Notification Consumer (success) ← payment-topic
 *          → Notification Consumer (failure) ← payment-failed-topic
 *
 * Scalability:
 *   - Stateless app: no in-memory session state. Multiple instances can run
 *     behind a load balancer. Kafka partition count (3) dictates max consumer
 *     parallelism per consumer group. Add more partitions to scale further.
 */
@SpringBootApplication
public class OrderPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderPaymentApplication.class, args);
    }
}
