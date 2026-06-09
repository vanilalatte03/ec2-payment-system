package com.teamec2.paymentsystem.domain.refund.repository;

import com.teamec2.paymentsystem.domain.refund.entity.Refund;
import com.teamec2.paymentsystem.domain.refund.enums.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    /**
     * 같은 결제의 같은 Idempotency-Key 요청이 이미 있는지 확인합니다.
     */
    Optional<Refund> findByPayment_IdAndIdempotencyKey(Long paymentId, String idempotencyKey);

    Optional<Refund> findByPortonePaymentIdAndPortoneCancellationId(
            String portonePaymentId,
            String portoneCancellationId
    );

    /**
     * 특정 결제에 특정 상태의 환불이 존재하는지 확인합니다.
     *
     * PROCESSING, PG_RESULT_UNKNOWN 상태의 환불이 있으면
     * 동시에 다른 환불이 들어오지 못하게 막는 데 사용합니다.
     */
    boolean existsByPayment_IdAndStatusIn(Long paymentId, Collection<RefundStatus> statuses);


    /**
     * 특정 결제의 특정 상태 환불 PG 금액 합계를 조회합니다.
     * coalesce를 사용해서 합계 결과가 없을 때 null이 아니라 0을 반환합니다.
     */
    @Query("""
            select coalesce(sum(r.pgRefundAmount), 0)
            from Refund r
            where r.payment.id = :paymentId
              and r.status = :status
            """)
    Long sumPgRefundAmountByPaymentIdAndStatus(
            @Param("paymentId") Long paymentId,
            @Param("status") RefundStatus status
    );

    /**
     * 완료된 환불의 PG 환불 금액 합계를 조회합니다.
     */
    default Long sumCompletedPgRefundAmount(Long paymentId) {
        return sumPgRefundAmountByPaymentIdAndStatus(
                paymentId,
                RefundStatus.COMPLETED
        );
    }

    /**
     * 특정 결제의 특정 상태 환불 포인트 금액 합계를 조회합니다.
     */
    @Query("""
            select coalesce(sum(r.pointRefundAmount), 0)
            from Refund r
            where r.payment.id = :paymentId
              and r.status = :status
            """)
    Long sumPointRefundAmountByPaymentIdAndStatus(
            @Param("paymentId") Long paymentId,
            @Param("status") RefundStatus status
    );


    /**
     * 완료된 환불의 포인트 환불 금액 합계를 조회합니다.
     */
    default Long sumCompletedPointRefundAmount(Long paymentId) {
        return sumPointRefundAmountByPaymentIdAndStatus(
                paymentId,
                RefundStatus.COMPLETED
        );
    }

    @Query("""
        select coalesce(sum(r.grossPointRefundAmount), 0)
        from Refund r
        where r.payment.id = :paymentId
          and r.status = :status
        """)
    Long sumGrossPointRefundAmountByPaymentIdAndStatus(
            @Param("paymentId") Long paymentId,
            @Param("status") RefundStatus status
    );

    default Long sumCompletedGrossPointRefundAmount(Long paymentId) {
        return sumGrossPointRefundAmountByPaymentIdAndStatus(
                paymentId,
                RefundStatus.COMPLETED
        );
    }

    @Query("""
        select coalesce(sum(r.grossPgRefundAmount), 0)
        from Refund r
        where r.payment.id = :paymentId
          and r.status = :status
        """)
    Long sumGrossPgRefundAmountByPaymentIdAndStatus(
            @Param("paymentId") Long paymentId,
            @Param("status") RefundStatus status
    );

    default Long sumCompletedGrossPgRefundAmount(Long paymentId) {
        return sumGrossPgRefundAmountByPaymentIdAndStatus(
                paymentId,
                RefundStatus.COMPLETED
        );
    }

    @Query("""
        select coalesce(sum(r.earnedPointRecoveryAmount), 0)
        from Refund r
        where r.payment.id = :paymentId
          and r.status = :status
        """)
    Long sumEarnedPointRecoveryAmountByPaymentIdAndStatus(
            @Param("paymentId") Long paymentId,
            @Param("status") RefundStatus status
    );

    default Long sumCompletedEarnedPointRecoveryAmount(Long paymentId) {
        return sumEarnedPointRecoveryAmountByPaymentIdAndStatus(
                paymentId,
                RefundStatus.COMPLETED
        );
    }
}
