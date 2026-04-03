package com.example.orderpayment.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {
    boolean existsByEventIdAndTopic(String eventId, String topic);
}
