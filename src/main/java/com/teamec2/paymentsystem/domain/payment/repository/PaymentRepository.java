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

    Optional<Payment> findByOrderId(Long orderId);

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

    /**
     * PortOne 결제 ID로 결제를 조회하면서 쓰기 잠금을 획득한다.
     *
     * <p>웹훅 기반 결제 확정은 내부 주문 ID를 받지 않고 PortOne 결제 ID만 받는다.
     * 같은 결제가 클라이언트 확정 요청과 웹훅으로 동시에 처리될 수 있으므로 비관적 쓰기 잠금으로
     * 중복 완료 처리를 막는다.
     *
     * <p>완료 응답 생성과 장바구니 정리에 주문과 사용자가 필요하므로 {@code order}, {@code user}를
     * 함께 fetch join 한다.
     *
     * @param portonePaymentId PortOne 결제 ID
     * @return 잠금이 적용되고 주문/사용자가 함께 로딩된 결제 엔티티
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Payment p
            join fetch p.order o
            join fetch o.user
            where p.portonePaymentId = :portonePaymentId
            """)
    Optional<Payment> findByPortonePaymentIdForUpdate(String portonePaymentId);

    /**
     * 결제 ID로 결제를 조회하면서 쓰기 잠금을 획득한다.
     *
     * <p>결제 완료 처리와 보상 취소 후 실패 처리 모두 같은 결제 상태를 변경한다.
     * 동시에 같은 결제를 변경하지 못하도록 비관적 쓰기 잠금을 사용한다.
     *
     * <p>응답 생성, 소유자 확인, 보상 실패 정리에 주문과 사용자가 필요하므로
     * {@code order}, {@code user}를 함께 fetch join 한다.
     *
     * @param paymentId 내부 결제 ID
     * @return 잠금이 적용되고 주문/사용자가 함께 로딩된 결제 엔티티
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Payment p
            join fetch p.order o
            join fetch o.user
            where p.id = :paymentId
            """)
    Optional<Payment> findByIdForUpdate(Long paymentId);

}
