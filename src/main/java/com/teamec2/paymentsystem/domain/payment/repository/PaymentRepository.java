package com.teamec2.paymentsystem.domain.payment.repository;

import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPortonePaymentId(String portonePaymentId);

    boolean existsByPortonePaymentId(String portonePaymentId);

    /**
     * 결제 확정 중복 처리를 막기 위해 주문 ID로 결제를 조회하면서 쓰기 잠금을 획득한다.
     *
     * <p>확정 요청의 멱등 응답 확인과 상태 변경이 같은 잠금 범위에서 처리되도록 사용한다.
     *
     * @param orderId 결제와 연결된 주문 ID
     * @return 잠금이 적용된 결제 엔티티
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p join fetch p.order where p.order.id = :orderId")
    Optional<Payment> findByOrderIdForUpdate(Long orderId);

}
