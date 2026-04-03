package com.example.orderpayment.common.exception;

public class DuplicateOrderException extends RuntimeException {
    public DuplicateOrderException(String idempotencyKey) {
        super("Order with idempotency key already exists: " + idempotencyKey);
    }
}
