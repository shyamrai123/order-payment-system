package com.example.orderpayment.common.listener;

import com.example.orderpayment.order.dto.OrderEventPublishRequest;
import com.example.orderpayment.order.producer.OrderProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventTransactionalListener {

    private final OrderProducer orderProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderEventPublishRequest request) {
        log.info("[ORDER] Transaction committed. Publishing to Kafka. orderId={}",
                request.getOrderEvent().getOrderId());
        orderProducer.publishOrderEvent(request.getOrderEvent());
    }
}