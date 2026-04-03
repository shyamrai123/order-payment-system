package com.example.orderpayment.order.repository;

import com.example.orderpayment.order.entity.Order;
import com.example.orderpayment.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    List<Order> findByCustomerId(String customerId);

    List<Order> findByStatus(OrderStatus status);
}
