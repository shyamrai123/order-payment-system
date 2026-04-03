package com.example.orderpayment.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderEventPublishRequest {
    private final OrderEvent orderEvent;
}
