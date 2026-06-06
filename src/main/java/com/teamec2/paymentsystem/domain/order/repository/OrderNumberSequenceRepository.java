package com.teamec2.paymentsystem.domain.order.repository;

import com.teamec2.paymentsystem.domain.order.entity.OrderNumberSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface OrderNumberSequenceRepository extends JpaRepository<OrderNumberSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sequence from OrderNumberSequence sequence where sequence.orderDate = :orderDate")
    Optional<OrderNumberSequence> findByOrderDateForUpdate(@Param("orderDate") LocalDate orderDate);
}
