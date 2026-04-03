package com.example.orderpayment.common.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Guards Kafka consumers against duplicate message processing.
 *
 * Usage in consumers:
 *   if (idempotencyService.isAlreadyProcessed(eventId, topic)) return;
 *   idempotencyService.markAsProcessed(eventId, topic);
 *   // ... process message
 *
 * The eventId is typically the Kafka message key (orderId) combined with offset,
 * or a UUID embedded in the message payload.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final ProcessedEventRepository repository;

    /**
     * Returns true if this (eventId, topic) pair has already been processed.
     * If true, the consumer should skip processing and log the skip.
     */
    @Transactional(readOnly = true)
    public boolean isAlreadyProcessed(String eventId, String topic) {
        boolean exists = repository.existsByEventIdAndTopic(eventId, topic);
        if (exists) {
            log.info("[IDEMPOTENCY] Skipping already-processed event. eventId={}, topic={}", eventId, topic);
        }
        return exists;
    }

    /**
     * Marks this (eventId, topic) pair as processed.
     * Call AFTER successfully processing the message to avoid gaps on crash.
     */
    @Transactional
    public void markAsProcessed(String eventId, String topic) {
        ProcessedEvent event = ProcessedEvent.builder()
                .eventId(eventId)
                .topic(topic)
                .processedAt(LocalDateTime.now())
                .build();
        repository.save(event);
        log.debug("[IDEMPOTENCY] Marked event as processed. eventId={}, topic={}", eventId, topic);
    }
}
