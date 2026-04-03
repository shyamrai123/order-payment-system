package com.example.orderpayment.common.idempotency;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA entity for the processed_events table.
 * Stores (eventId, topic) pairs to detect and skip Kafka redeliveries.
 */
@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Id
    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
