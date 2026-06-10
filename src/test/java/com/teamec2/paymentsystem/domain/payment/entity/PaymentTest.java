package com.teamec2.paymentsystem.domain.payment.entity;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    @Test
    void 결제대기생성_정상금액이면_PENDING상태와금액정보를저장한다() {
        // given
        Order order = 주문_생성();

        // when
        Payment payment = Payment.createPending(order, 1000L, 200L, 800L, 8L);

        // then
        assertThat(payment.getOrder()).isEqualTo(order);
        assertThat(payment.getPortonePaymentId()).startsWith("pay_");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPaymentType()).isEqualTo(PaymentType.POINT_CARD);
        assertThat(payment.getTotalAmount()).isEqualTo(1000L);
        assertThat(payment.getUsedPointAmount()).isEqualTo(200L);
        assertThat(payment.getPgAmount()).isEqualTo(800L);
        assertThat(payment.getRewardPointAmount()).isEqualTo(8L);
    }

    @Test
    void 결제대기생성_주문이없으면_VALIDATION_FAILED가발생한다() {
        // when
        // then
        assertThatThrownBy(() -> Payment.createPending(null, 1000L, 200L, 800L, 8L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void 결제대기생성_금액중하나가없으면_MISSING_REQUIRED_FIELD가발생한다() {
        // given
        Order order = 주문_생성();

        // when
        // then
        assertThatThrownBy(() -> Payment.createPending(order, null, 200L, 800L, 8L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
    }

    @Test
    void 결제대기생성_금액이음수면_VALIDATION_FAILED가발생한다() {
        // given
        Order order = 주문_생성();

        // when
        // then
        assertThatThrownBy(() -> Payment.createPending(order, 1000L, -1L, 1001L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void 결제대기생성_총금액이사용포인트와PG금액합계와다르면_PAYMENT_AMOUNT_MISMATCH가발생한다() {
        // given
        Order order = 주문_생성();

        // when
        // then
        assertThatThrownBy(() -> Payment.createPending(order, 1000L, 200L, 700L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }

    @Test
    void 결제대기상태_완료처리하면_COMPLETED와승인시각을저장한다() {
        // given
        Payment payment = 결제_생성();
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);

        // when
        payment.complete(approvedAt);

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getApprovedAt()).isEqualTo(approvedAt);
    }

    @Test
    void 결제완료처리_승인시각이없으면_MISSING_REQUIRED_FIELD가발생한다() {
        // given
        Payment payment = 결제_생성();

        // when
        // then
        assertThatThrownBy(() -> payment.complete(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
    }

    @Test
    void 결제대기상태_실패처리하면_FAILED와실패시각을저장한다() {
        // given
        Payment payment = 결제_생성();
        LocalDateTime failedAt = LocalDateTime.of(2026, 6, 1, 12, 30);

        // when
        payment.fail(failedAt);

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailedAt()).isEqualTo(failedAt);
    }

    @Test
    void 결제실패처리_실패시각이없으면_MISSING_REQUIRED_FIELD가발생한다() {
        // given
        Payment payment = 결제_생성();

        // when
        // then
        assertThatThrownBy(() -> payment.fail(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
    }

    @Test
    void 결제완료재시도대상으로_표시하면_승인시각과다음시도시각을저장한다() {
        // given
        Payment payment = 결제_생성();
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 12, 31);

        // when
        payment.markConfirmRetryRequired(approvedAt, "DB 완료 실패", now);

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFIRM_RETRY_REQUIRED);
        assertThat(payment.getConfirmRetryApprovedAt()).isEqualTo(approvedAt);
        assertThat(payment.getNextConfirmAttemptAt()).isEqualTo(now);
        assertThat(payment.getConfirmLastErrorMessage()).isEqualTo("DB 완료 실패");
    }

    @Test
    void 완료재시도상태_완료처리하면_COMPLETED가되고_재시도정보를초기화한다() {
        // given
        Payment payment = 결제_생성();
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);
        payment.markConfirmRetryRequired(approvedAt, "DB 완료 실패", approvedAt.plusMinutes(1));

        // when
        payment.complete(approvedAt);

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getNextConfirmAttemptAt()).isNull();
        assertThat(payment.getConfirmRetryApprovedAt()).isNull();
        assertThat(payment.getConfirmLastErrorMessage()).isNull();
    }

    @Test
    void 완료재시도상태_최대횟수를초과하면_다음시도시각을비우고_실패를반환한다() {
        // given
        Payment payment = 결제_생성();
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);
        payment.markConfirmRetryRequired(approvedAt, "DB 완료 실패", approvedAt.plusMinutes(1));

        for (int i = 0; i < 5; i++) {
            payment.markConfirmRetry("일시 장애", approvedAt.plusMinutes(2 + i));
        }

        // when
        boolean retryScheduled = payment.markConfirmRetry("계속 실패", approvedAt.plusMinutes(10));

        // then
        assertThat(retryScheduled).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFIRM_RETRY_REQUIRED);
        assertThat(payment.getConfirmRetryCount()).isEqualTo(6);
        assertThat(payment.getNextConfirmAttemptAt()).isNull();
        assertThat(payment.getConfirmProcessingStartedAt()).isNull();
        assertThat(payment.getConfirmLastErrorMessage()).isEqualTo("계속 실패");
    }

    @Test
    void 보상취소대상으로_표시하면_COMPENSATION_REQUIRED가된다() {
        // given
        Payment payment = 결제_생성();

        // when
        payment.markCompensationRequired();

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPENSATION_REQUIRED);
    }

    @Test
    void 보상취소필요상태_보상취소실패처리하면_COMPENSATION_FAILED가된다() {
        // given
        Payment payment = 결제_생성();
        payment.markCompensationRequired();

        // when
        payment.markCompensationFailed();

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPENSATION_FAILED);
    }

    @Test
    void 보상취소필요상태_실패처리하면_FAILED가된다() {
        // given
        Payment payment = 결제_생성();
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 12, 30);
        payment.markCompensationRequired();

        // when
        payment.fail(now);

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void 결제완료상태_부분환불처리하면_PARTIAL_REFUNDED가된다() {
        // given
        Payment payment = 결제_생성();
        payment.complete(LocalDateTime.of(2026, 6, 1, 12, 30));

        // when
        payment.markAsPartialRefunded();

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_REFUNDED);
    }

    @Test
    void 부분환불상태_다시부분환불처리하면_PARTIAL_REFUNDED를유지한다() {
        // given
        Payment payment = 결제_생성();
        payment.complete(LocalDateTime.of(2026, 6, 1, 12, 30));
        payment.markAsPartialRefunded();

        // when
        payment.markAsPartialRefunded();

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_REFUNDED);
    }

    @Test
    void 결제완료상태_전액환불처리하면_REFUNDED가된다() {
        // given
        Payment payment = 결제_생성();
        payment.complete(LocalDateTime.of(2026, 6, 1, 12, 30));

        // when
        payment.markAsRefunded();

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FULL_REFUNDED);
    }

    @Test
    void 부분환불상태_전액환불처리하면_REFUNDED가된다() {
        // given
        Payment payment = 결제_생성();
        payment.complete(LocalDateTime.of(2026, 6, 1, 12, 30));
        payment.markAsPartialRefunded();

        // when
        payment.markAsRefunded();

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FULL_REFUNDED);
    }

    @Test
    void 허용되지않은상태전이면_CONFLICT가발생한다() {
        // given
        Payment payment = 결제_생성();

        // when
        // then
        assertThatThrownBy(payment::markAsRefunded)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void 실패상태에서_완료처리하면_CONFLICT가발생한다() {
        // given
        Payment payment = 결제_생성();
        payment.fail(LocalDateTime.of(2026, 6, 1, 12, 30));

        // when
        // then
        assertThatThrownBy(() -> payment.complete(LocalDateTime.of(2026, 6, 1, 12, 31)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }

    private Payment 결제_생성() {
        return Payment.createPending(주문_생성(), 1000L, 200L, 800L, 8L);
    }

    private Order 주문_생성() {
        return Order.create(
                회원_생성(),
                "ORDER-001",
                1000L,
                200L
        );
    }

    private User 회원_생성() {
        return User.create(
                "test@example.com",
                "password",
                "테스트유저",
                "010-1234-5678"
        );
    }
}
