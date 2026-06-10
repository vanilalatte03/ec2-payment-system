package com.teamec2.paymentsystem.domain.payment.repository;

import com.teamec2.paymentsystem.domain.payment.entity.PaymentCompensationOutbox;
import com.teamec2.paymentsystem.domain.payment.enums.PaymentCompensationOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentCompensationOutboxRepository extends JpaRepository<PaymentCompensationOutbox, Long> {

    Optional<PaymentCompensationOutbox> findByPaymentId(Long paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select pco
            from PaymentCompensationOutbox pco
            join fetch pco.payment p
            join fetch p.order o
            join fetch o.user
            where pco.id = :id
            """)
    Optional<PaymentCompensationOutbox> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select pco
            from PaymentCompensationOutbox pco
            join fetch pco.payment p
            join fetch p.order o
            join fetch o.user
            where p.id = :paymentId
            """)
    Optional<PaymentCompensationOutbox> findByPaymentIdForUpdate(@Param("paymentId") Long paymentId);

    @Query("""
            select pco.id
            from PaymentCompensationOutbox pco
            where pco.status = :status
              and pco.nextAttemptAt <= :now
            order by pco.id
            """)
    List<Long> findDueIds(
            @Param("status") PaymentCompensationOutboxStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    default List<Long> findDuePendingIds(LocalDateTime now, Pageable pageable) {
        return findDueIds(
                PaymentCompensationOutboxStatus.PENDING,
                now,
                pageable
        );
    }

    @Query("""
            select pco.id
            from PaymentCompensationOutbox pco
            where pco.status = :status
              and pco.processingStartedAt <= :threshold
            order by pco.id
            """)
    List<Long> findStaleProcessingIds(
            @Param("status") PaymentCompensationOutboxStatus status,
            @Param("threshold") LocalDateTime threshold,
            Pageable pageable
    );

    default List<Long> findStaleProcessingIds(LocalDateTime threshold, Pageable pageable) {
        return findStaleProcessingIds(
                PaymentCompensationOutboxStatus.PROCESSING,
                threshold,
                pageable
        );
    }
}
