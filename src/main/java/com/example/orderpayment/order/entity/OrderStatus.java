package com.example.orderpayment.order.entity;

/**
 * Valid order status transitions:
 * PENDING → PAYMENT_PROCESSING → PAYMENT_SUCCESS
 *                              → PAYMENT_FAILED
 * PENDING → CANCELLED
 *
 * Any other transition is invalid and throws InvalidOrderStateException.
 */
public enum OrderStatus {
    PENDING,
    PAYMENT_PROCESSING,
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    CANCELLED
}
