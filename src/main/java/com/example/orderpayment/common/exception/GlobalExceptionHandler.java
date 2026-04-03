package com.example.orderpayment.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralised exception handler using RFC 7807 ProblemDetail format.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleOrderNotFound(OrderNotFoundException ex) {
        log.warn("Order not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Order Not Found");
        pd.setType(URI.create("/errors/order-not-found"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ProblemDetail handleInvalidOrderState(InvalidOrderStateException ex) {
        log.warn("Invalid order state transition: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Invalid Order State");
        pd.setType(URI.create("/errors/invalid-order-state"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(DuplicateOrderException.class)
    public ProblemDetail handleDuplicateOrder(DuplicateOrderException ex) {
        log.warn("Duplicate order detected: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Duplicate Order");
        pd.setType(URI.create("/errors/duplicate-order"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.warn("Validation failed: {}", fieldErrors);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Request validation failed");
        pd.setTitle("Validation Error");
        pd.setType(URI.create("/errors/validation"));
        pd.setProperty("timestamp", Instant.now());
        pd.setProperty("fieldErrors", fieldErrors);
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
        pd.setTitle("Internal Server Error");
        pd.setType(URI.create("/errors/internal"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
