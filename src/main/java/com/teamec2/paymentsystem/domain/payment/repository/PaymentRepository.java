package com.teamec2.paymentsystem.domain.payment.repository;

import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPortonePaymentId(String portonePaymentId);

    boolean existsByPortonePaymentId(String portonePaymentId);

}
