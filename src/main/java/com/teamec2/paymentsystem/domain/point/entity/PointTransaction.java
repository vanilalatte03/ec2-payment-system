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
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_point_transactions_idempotency_key",
                        columnNames = "idempotency_key")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PointTransaction {

    private static final String PAYMENT_KEY_PREFIX = "PAYMENT";
    private static final String REFUND_KEY_PREFIX = "REFUND";

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
     * - 거래 금액은 음수 없이 저장합니다
     * - 잔액의 증가·감소 방향은 type으로 구분하며
     * EARN_CANCEL 처리 결과로 회원의 포인트 잔액은 음수가 될 수 없습니다.
     */
    @Column(nullable = false)
    private Long amount;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 포인트 거래의 멱등 키입니다.
     * 결제 과정 원장은 PAYMENT:{paymentId}:{type}, 환불 과정 원장은 REFUND:{refundId}:{type} 형식을 사용합니다.
     */
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    private PointTransaction(
            User user,
            Payment payment,
            Refund refund,
            PointTransactionType type,
            Long amount,
            String idempotencyKey
    ) {
        validateRequiredValues(user, payment, type, amount);
        validateIdempotencyKey(idempotencyKey);

        this.user = user;
        this.payment = payment;
        this.refund = refund;
        this.type = type;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }

    /**
     * 결제 과정에서 발생하는 포인트 원장을 생성합니다.
     * USE_RESERVE, USE, USE_CANCEL, EARN처럼 paymentId를 기준으로 중복 여부를 판단하는 거래에 사용합니다.
     */
    public static PointTransaction createForPayment(
          User user,
          Payment payment,
          PointTransactionType type,
          Long amount
    ) {
        validatePaymentType(type);

        String idempotencyKey = paymentIdempotencyKey(payment, type);

        return new PointTransaction(
                user,
                payment,
                null,
                type,
                amount,
                idempotencyKey
        );
    }

    /**
     * 환불 과정에서 발생하는 포인트 원장을 생성합니다.
     * USE_RESTORE, EARN_CANCEL처럼 refundId를 기준으로 중복 여부를 판단하는 거래에 사용합니다.
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

        String idempotencyKey = refundIdempotencyKey(refund, type);

        return new PointTransaction(
                user,
                payment,
                refund,
                type,
                amount,
                idempotencyKey
        );
    }

    /**
     * 예약 포인트 사용 원장을 최종 사용 원장으로 확정합니다.
     * 주문 생성 시점에 이미 잔액은 차감되었으므로 추가 잔액 변경 없이 타입과 멱등 키만 USE 기준으로 변경합니다.
     */
    public void confirmUse() {
        if (this.type != PointTransactionType.USE_RESERVE) {
            throw new BusinessException(ErrorCode.INVALID_POINT_TRANSACTION_TYPE);
        }

        this.type = PointTransactionType.USE;
        this.idempotencyKey = paymentIdempotencyKey(this.payment, PointTransactionType.USE);
    }

    /**
     * 결제성 포인트 거래의 멱등 키를 생성합니다.
     * 같은 결제의 같은 거래 타입은 한 번만 처리되어야 하므로 paymentId와 type을 조합합니다.
     */
    public static String paymentIdempotencyKey(Payment payment, PointTransactionType type) {
        if (payment == null || payment.getId() == null || type == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        return "%s:%d:%s".formatted(PAYMENT_KEY_PREFIX, payment.getId(), type.name());
    }

    /**
     * 환불성 포인트 거래의 멱등 키를 생성합니다.
     * 같은 refundId와 거래 타입에 대해서는 동일한 키가 생성되어야 하며,이를 통해 환불 요청 재시도 시 포인트가 중복 변경되는 것을 방지합니다.
     */
    public static String refundIdempotencyKey(Refund refund, PointTransactionType type) {

        if (refund == null || refund.getId() == null || type == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        return "REFUND:%d:%s".formatted(refund.getId(), type.name());
    }

    /**
     * 멱등 키가 비어 있지 않은지 검증합니다.
     * 멱등 키가 없으면 중복 원장을 DB unique 제약으로 막을 수 없기 때문에 필수값으로 처리합니다.
     */
    private static void validateIdempotencyKey(String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }
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

        if (amount < 0) {
            throw new BusinessException(ErrorCode.INVALID_POINT_TRANSACTION_AMOUNT);
        }

        // EARN_CANCEL은 실제 회수 금액이 0원이어도 멱등 처리를 기록하기 위해 0원 원장을 허용합니다.
        if (amount == 0 && type != PointTransactionType.EARN_CANCEL) {
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
