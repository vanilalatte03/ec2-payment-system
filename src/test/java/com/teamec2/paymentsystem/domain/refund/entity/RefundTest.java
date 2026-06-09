package com.teamec2.paymentsystem.domain.refund.entity;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.refund.enums.RefundStatus;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundTest {

    @Test
    void 환불생성_정산금액관계가맞으면_PROCESSING상태로생성한다() {
        // given
        Payment payment = 결제(1L, 10L);

        // when
        Refund refund = 환불(
                payment,
                1_000L,
                200L,
                800L,
                250L,
                850L,
                100L,
                50L,
                0L,
                50L
        );

        // then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(refund.getPortonePaymentId()).isEqualTo(payment.getPortonePaymentId());
        assertThat(refund.getPointRefundAmount()).isEqualTo(200L);
        assertThat(refund.getPgRefundAmount()).isEqualTo(800L);
    }

    @Test
    void 환불생성_필수값이없으면_MISSING_REQUIRED_FIELD가발생한다() {
        // given
        Payment payment = 결제(1L, 10L);

        // when
        // then
        assertThatThrownBy(() -> 환불(
                payment,
                1_000L,
                200L,
                800L,
                null,
                850L,
                100L,
                50L,
                0L,
                50L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);

        assertThatThrownBy(() -> Refund.createRefund(
                " ",
                "request-hash",
                payment.getOrder(),
                payment,
                "환불 사유",
                1_000L,
                200L,
                800L,
                250L,
                850L,
                100L,
                50L,
                0L,
                50L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
    }

    @Test
    void 환불생성_금액이음수이거나합계가맞지않으면_VALIDATION_FAILED가발생한다() {
        // given
        Payment payment = 결제(1L, 10L);

        // when
        // then
        assertThatThrownBy(() -> 환불(
                payment,
                1_000L,
                -1L,
                1_001L,
                250L,
                850L,
                100L,
                50L,
                0L,
                50L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        assertThatThrownBy(() -> 환불(
                payment,
                1_000L,
                100L,
                800L,
                250L,
                850L,
                100L,
                50L,
                0L,
                50L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void 환불생성_정산금액관계가맞지않으면_VALIDATION_FAILED가발생한다() {
        // given
        Payment payment = 결제(1L, 10L);

        // when
        // then
        assertThatThrownBy(() -> 환불(
                payment,
                1_000L,
                200L,
                800L,
                250L,
                850L,
                100L,
                251L,
                0L,
                50L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        assertThatThrownBy(() -> 환불(
                payment,
                1_000L,
                200L,
                800L,
                250L,
                850L,
                99L,
                50L,
                0L,
                50L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void 환불생성_주문과결제의주문이다르면_VALIDATION_FAILED가발생한다() {
        // given
        Payment payment = 결제(1L, 10L);
        Order otherOrder = 주문(2L);

        // when
        // then
        assertThatThrownBy(() -> Refund.createRefund(
                "refund-key",
                "request-hash",
                otherOrder,
                payment,
                "환불 사유",
                1_000L,
                200L,
                800L,
                250L,
                850L,
                100L,
                50L,
                0L,
                50L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void 환불완료_PROCESSING과PG_RESULT_UNKNOWN만_완료가능하다() {
        // given
        Refund processingRefund = 환불(결제(1L, 10L));
        Refund pgUnknownRefund = 환불(결제(2L, 20L));
        Refund failedRefund = 환불(결제(3L, 30L));
        Refund completedRefund = 환불(결제(4L, 40L));

        pgUnknownRefund.markPgResultUnknown("PG 결과 미확정");
        failedRefund.fail("PG 취소 실패");
        completedRefund.complete(LocalDateTime.of(2026, 6, 1, 12, 0));

        // when
        processingRefund.complete(LocalDateTime.of(2026, 6, 1, 13, 0));
        pgUnknownRefund.complete(LocalDateTime.of(2026, 6, 1, 14, 0));

        // then
        assertThat(processingRefund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(pgUnknownRefund.getStatus()).isEqualTo(RefundStatus.COMPLETED);

        assertThatThrownBy(() -> failedRefund.complete(LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFUND_STATUS);
        assertThatThrownBy(() -> completedRefund.complete(LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFUND_STATUS);
    }

    @Test
    void 환불완료_완료시각이없으면_MISSING_REQUIRED_FIELD가발생한다() {
        // given
        Refund refund = 환불(결제(1L, 10L));

        // when
        // then
        assertThatThrownBy(() -> refund.complete(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
    }

    @Test
    void 환불실패_PROCESSING과PG_RESULT_UNKNOWN만_실패가능하고_이미실패면멱등처리한다() {
        // given
        Refund processingRefund = 환불(결제(1L, 10L));
        Refund pgUnknownRefund = 환불(결제(2L, 20L));
        Refund failedRefund = 환불(결제(3L, 30L));
        Refund completedRefund = 환불(결제(4L, 40L));

        pgUnknownRefund.markPgResultUnknown("PG 결과 미확정");
        failedRefund.fail("첫 실패");
        completedRefund.complete(LocalDateTime.of(2026, 6, 1, 12, 0));

        // when
        processingRefund.fail("PG 취소 실패");
        pgUnknownRefund.fail("PG 취소 실패");
        failedRefund.fail("두 번째 실패");

        // then
        assertThat(processingRefund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(pgUnknownRefund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(failedRefund.getFailedReason()).isEqualTo("첫 실패");

        assertThatThrownBy(() -> completedRefund.fail("실패 처리"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFUND_STATUS);
    }

    @Test
    void 환불실패_사유가없으면_MISSING_REQUIRED_FIELD가발생하고_긴사유는500자로자른다() {
        // given
        Refund refund = 환불(결제(1L, 10L));
        Refund longReasonRefund = 환불(결제(2L, 20L));
        String longReason = "a".repeat(600);

        // when
        longReasonRefund.fail(longReason);

        // then
        assertThat(longReasonRefund.getFailedReason()).hasSize(500);
        assertThatThrownBy(() -> refund.fail(" "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
    }

    @Test
    void PG결과미확정_PROCESSING에서만가능하고_이미미확정이면멱등처리한다() {
        // given
        Refund refund = 환불(결제(1L, 10L));
        Refund completedRefund = 환불(결제(2L, 20L));
        completedRefund.complete(LocalDateTime.of(2026, 6, 1, 12, 0));

        // when
        refund.markPgResultUnknown("첫 미확정");
        refund.markPgResultUnknown("두 번째 미확정");

        // then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PG_RESULT_UNKNOWN);
        assertThat(refund.getPgResultUnknownReason()).isEqualTo("첫 미확정");
        assertThatThrownBy(() -> completedRefund.markPgResultUnknown("미확정"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFUND_STATUS);
    }

    @Test
    void PG결과미확정_사유가없으면_MISSING_REQUIRED_FIELD가발생하고_긴사유는500자로자른다() {
        // given
        Refund refund = 환불(결제(1L, 10L));
        Refund longReasonRefund = 환불(결제(2L, 20L));
        String longReason = "b".repeat(600);

        // when
        longReasonRefund.markPgResultUnknown(longReason);

        // then
        assertThat(longReasonRefund.getPgResultUnknownReason()).hasSize(500);
        assertThatThrownBy(() -> refund.markPgResultUnknown(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
    }

    @Test
    void PortOne취소ID기록_비어있으면무시하고_다른값이면_VALIDATION_FAILED가발생한다() {
        // given
        Refund refund = 환불(결제(1L, 10L));

        // when
        refund.recordPortoneCancellationId(null);
        refund.recordPortoneCancellationId(" ");
        refund.recordPortoneCancellationId("cancel-1");
        refund.recordPortoneCancellationId("cancel-1");

        // then
        assertThat(refund.getPortoneCancellationId()).isEqualTo("cancel-1");
        assertThatThrownBy(() -> refund.recordPortoneCancellationId("cancel-2"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    private Refund 환불(Payment payment) {
        return 환불(
                payment,
                1_000L,
                200L,
                800L,
                250L,
                850L,
                100L,
                50L,
                0L,
                50L
        );
    }

    private Refund 환불(
            Payment payment,
            Long refundAmount,
            Long pointRefundAmount,
            Long pgRefundAmount,
            Long grossPointRefundAmount,
            Long grossPgRefundAmount,
            Long earnedPointRecoveryAmount,
            Long recoveredFromUsedPoint,
            Long recoveredFromBalance,
            Long deductedFromPgRefund
    ) {
        return Refund.createRefund(
                "refund-key-" + payment.getId(),
                "request-hash-" + payment.getId(),
                payment.getOrder(),
                payment,
                "환불 사유",
                refundAmount,
                pointRefundAmount,
                pgRefundAmount,
                grossPointRefundAmount,
                grossPgRefundAmount,
                earnedPointRecoveryAmount,
                recoveredFromUsedPoint,
                recoveredFromBalance,
                deductedFromPgRefund
        );
    }

    private Payment 결제(Long orderId, Long paymentId) {
        Order order = 주문(orderId);
        Payment payment = Payment.createPending(order, 1_000L, 200L, 800L, 8L);
        ReflectionTestUtils.setField(payment, "id", paymentId);
        return payment;
    }

    private Order 주문(Long orderId) {
        User user = User.create("user-" + orderId + "@example.com", "Password123!", "홍길동", "010-1234-5678");
        ReflectionTestUtils.setField(user, "id", orderId);
        Order order = Order.create(user, "ORDER-" + orderId, 1_000L, 200L);
        ReflectionTestUtils.setField(order, "id", orderId);
        return order;
    }
}
