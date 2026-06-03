package com.teamec2.paymentsystem.domain.point.entity;

import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.point.enums.PointTransactionType;
import com.teamec2.paymentsystem.domain.refund.entity.Refund;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "point_transactions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"refund_id", "type"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PointTransaction {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id")
    private Refund refund;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PointTransactionType type;

    /**
     * - 거래 금액은 절댓값인 양수로 저장합니다.
     * - 잔액의 증가·감소 방향은 type으로 구분하며
     * EARN_CANCEL 처리 결과로 회원의 포인트 잔액은 음수가 될 수 없습니다.
     */
    @Column(nullable = false)
    private Long amount;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;


    private PointTransaction(
            User user,
            Payment payment,
            Refund refund,
            PointTransactionType type,
            Long amount
    ) {
        validateRequiredValues(user, payment, type, amount);

        this.user = user;
        this.payment = payment;
        this.refund = refund;
        this.type = type;
        this.amount = amount;
    }

    /**
     * 최초 결제 과정에서 발생하는 포인트 사용 혹은 적립 내역
     */
    public static PointTransaction createForPayment(
          User user,
          Payment payment,
          PointTransactionType type,
          Long amount
    ) {
        validatePaymentType(type);
        return new PointTransaction(user, payment, null, type, amount);
    }

    /**
     * 환불 과정에서 발생하는 포인트 복구 또는 적립 취소 내역
     */
    public static PointTransaction createForRefund(
            User user,
            Payment payment,
            Refund refund,
            PointTransactionType type,
            Long amount
    ) {
        validateRefundType(type);
        validateRefundExists(refund);
        return new PointTransaction(user, payment, refund, type, amount);
    }

    /**
     * 예약된 포인트 사용 내역을 결제 완료 후 최종 사용 내역으로 확정합니다.
     * 이미 주문 생성 시점에 포인트 잔액은 차감했으므로, 여기서는 거래 타입만 USE로 변경합니다.
     */
    public void confirmUse() {
        if (this.type != PointTransactionType.USE_RESERVE) {
            throw new BusinessException(ErrorCode.INVALID_POINT_TRANSACTION_TYPE);
        }

        this.type = PointTransactionType.USE;
    }

    private static void validateRequiredValues(
            User user,
            Payment payment,
            PointTransactionType type,
            Long amount
    ) {
        if (user == null || payment == null || type == null || amount == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_POINT_TRANSACTION_AMOUNT);
        }
    }

    private static void validatePaymentType(PointTransactionType type) {
        if (type == null || !type.isPaymentType()) {
            throw new BusinessException(ErrorCode.INVALID_POINT_TRANSACTION_TYPE);
        }
    }

    private static void validateRefundType(PointTransactionType type) {
        if (type == null || !type.isRefundType()) {
            throw new BusinessException(ErrorCode.INVALID_POINT_TRANSACTION_TYPE);
        }
    }

    private static void validateRefundExists(Refund refund) {
        if (refund == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }
    }
}
