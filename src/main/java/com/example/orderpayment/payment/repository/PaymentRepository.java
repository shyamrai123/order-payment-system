package com.example.orderpayment.payment.repository;

import com.example.orderpayment.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    List<Payment> findByStatus(com.example.orderpayment.payment.entity.PaymentStatus status);
}
