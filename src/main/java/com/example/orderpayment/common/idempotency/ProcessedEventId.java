package com.example.orderpayment.common.idempotency;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for ProcessedEvent (eventId + topic).
 */
public class ProcessedEventId implements Serializable {

    private String eventId;
    private String topic;

    public ProcessedEventId() {}

    public ProcessedEventId(String eventId, String topic) {
        this.eventId = eventId;
        this.topic = topic;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProcessedEventId that)) return false;
        return Objects.equals(eventId, that.eventId) && Objects.equals(topic, that.topic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, topic);
    }
}
