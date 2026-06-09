package com.teamec2.paymentsystem.domain.refund.entity;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.refund.enums.RefundOutboxStatus;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundOutboxTest {

    @Test
    void 환불Outbox생성_필수값이있으면_PENDING상태로생성한다() {
        // given
        Refund refund = 환불(결제(1L, 10L));
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 12, 0);

        // when
        RefundOutbox outbox = RefundOutbox.create(refund, now);

        // then
        assertThat(outbox.getRefund()).isSameAs(refund);
        assertThat(outbox.getStatus()).isEqualTo(RefundOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isZero();
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now);
    }

    @Test
    void 환불Outbox생성_필수값이없으면_MISSING_REQUIRED_FIELD가발생한다() {
        // given
        Refund refund = 환불(결제(1L, 10L));

        // when
        // then
        assertThatThrownBy(() -> RefundOutbox.create(null, LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
        assertThatThrownBy(() -> RefundOutbox.create(refund, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
    }

    @Test
    void 환불Outbox_처리중전환은_PENDING에서만가능하다() {
        // given
        RefundOutbox outbox = RefundOutbox.create(환불(결제(1L, 10L)), LocalDateTime.now());
        LocalDateTime processingStartedAt = LocalDateTime.of(2026, 6, 1, 12, 0);

        // when
        outbox.markProcessing(processingStartedAt);

        // then
        assertThat(outbox.getStatus()).isEqualTo(RefundOutboxStatus.PROCESSING);
        assertThat(outbox.getProcessingStartedAt()).isEqualTo(processingStartedAt);
        assertThatThrownBy(() -> outbox.markProcessing(LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFUND_OUTBOX_STATUS);
    }

    @Test
    void 환불Outbox_처리중전환시각이없으면_MISSING_REQUIRED_FIELD가발생한다() {
        // given
        RefundOutbox outbox = RefundOutbox.create(환불(결제(1L, 10L)), LocalDateTime.now());

        // when
        // then
        assertThatThrownBy(() -> outbox.markProcessing(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
    }

    @Test
    void 환불Outbox_웹훅복구처리중전환은_FAILED에서만가능하고_오류메시지를초기화한다() {
        // given
        RefundOutbox outbox = RefundOutbox.create(환불(결제(1L, 10L)), LocalDateTime.now());
        outbox.markFailed("실패");
        LocalDateTime recoveryStartedAt = LocalDateTime.of(2026, 6, 1, 13, 0);

        // when
        outbox.markProcessingForWebhookRecovery(recoveryStartedAt);

        // then
        assertThat(outbox.getStatus()).isEqualTo(RefundOutboxStatus.PROCESSING);
        assertThat(outbox.getProcessingStartedAt()).isEqualTo(recoveryStartedAt);
        assertThat(outbox.getLastErrorMessage()).isNull();

        assertThatThrownBy(() -> outbox.markProcessingForWebhookRecovery(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
    }

    @Test
    void 환불Outbox_웹훅복구처리중전환_FAILED가아니면_INVALID_REFUND_OUTBOX_STATUS가발생한다() {
        // given
        RefundOutbox outbox = RefundOutbox.create(환불(결제(1L, 10L)), LocalDateTime.now());

        // when
        // then
        assertThatThrownBy(() -> outbox.markProcessingForWebhookRecovery(LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFUND_OUTBOX_STATUS);
    }

    @Test
    void 환불Outbox_성공처리는_PROCESSING에서만가능하고_이미성공이면멱등처리한다() {
        // given
        RefundOutbox outbox = RefundOutbox.create(환불(결제(1L, 10L)), LocalDateTime.now());
        RefundOutbox pendingOutbox = RefundOutbox.create(환불(결제(2L, 20L)), LocalDateTime.now());

        outbox.markProcessing(LocalDateTime.now());

        // when
        outbox.markSucceeded();
        outbox.markSucceeded();

        // then
        assertThat(outbox.getStatus()).isEqualTo(RefundOutboxStatus.SUCCEEDED);
        assertThat(outbox.getProcessingStartedAt()).isNull();
        assertThatThrownBy(pendingOutbox::markSucceeded)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFUND_OUTBOX_STATUS);
    }

    @Test
    void 환불Outbox_재시도는5회까지예약하고_6회째FAILED로전환한다() {
        // given
        RefundOutbox outbox = RefundOutbox.create(
                환불(결제(1L, 10L)),
                LocalDateTime.of(2026, 6, 1, 12, 0)
        );
        LocalDateTime retryBaseTime = LocalDateTime.of(2026, 6, 1, 12, 0);

        // when
        boolean firstRetry = 재시도(outbox, "첫 실패", retryBaseTime);
        boolean secondRetry = 재시도(outbox, "두 번째 실패", retryBaseTime.plusMinutes(1));
        boolean thirdRetry = 재시도(outbox, "세 번째 실패", retryBaseTime.plusMinutes(2));
        boolean fourthRetry = 재시도(outbox, "네 번째 실패", retryBaseTime.plusMinutes(3));
        boolean fifthRetry = 재시도(outbox, "다섯 번째 실패", retryBaseTime.plusMinutes(4));
        boolean sixthRetry = 재시도(outbox, "여섯 번째 실패", retryBaseTime.plusMinutes(5));

        // then
        assertThat(firstRetry).isTrue();
        assertThat(secondRetry).isTrue();
        assertThat(thirdRetry).isTrue();
        assertThat(fourthRetry).isTrue();
        assertThat(fifthRetry).isTrue();
        assertThat(sixthRetry).isFalse();
        assertThat(outbox.getRetryCount()).isEqualTo(6);
        assertThat(outbox.getStatus()).isEqualTo(RefundOutboxStatus.FAILED);
        assertThat(outbox.getLastErrorMessage()).startsWith("최대 재시도 횟수를 초과했습니다.");
        assertThat(outbox.getProcessingStartedAt()).isNull();
    }

    @Test
    void 환불Outbox_재시도예약은_PROCESSING에서만가능하고_시각이필수다() {
        // given
        RefundOutbox outbox = RefundOutbox.create(환불(결제(1L, 10L)), LocalDateTime.now());

        // when
        // then
        assertThatThrownBy(() -> outbox.markRetry("실패", LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFUND_OUTBOX_STATUS);

        outbox.markProcessing(LocalDateTime.now());
        assertThatThrownBy(() -> outbox.markRetry("실패", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
    }

    @Test
    void 환불Outbox_재시도실패사유는_null을허용하고_긴사유는500자로자른다() {
        // given
        RefundOutbox nullReasonOutbox = RefundOutbox.create(환불(결제(1L, 10L)), LocalDateTime.now());
        RefundOutbox longReasonOutbox = RefundOutbox.create(환불(결제(2L, 20L)), LocalDateTime.now());
        String longReason = "x".repeat(600);

        nullReasonOutbox.markProcessing(LocalDateTime.now());
        longReasonOutbox.markProcessing(LocalDateTime.now());

        // when
        nullReasonOutbox.markRetry(null, LocalDateTime.now());
        longReasonOutbox.markRetry(longReason, LocalDateTime.now());

        // then
        assertThat(nullReasonOutbox.getLastErrorMessage()).isNull();
        assertThat(longReasonOutbox.getLastErrorMessage()).hasSize(500);
    }

    @Test
    void 환불Outbox_실패처리는_PENDING과PROCESSING에서가능하고_이미실패면멱등처리한다() {
        // given
        RefundOutbox pendingOutbox = RefundOutbox.create(환불(결제(1L, 10L)), LocalDateTime.now());
        RefundOutbox processingOutbox = RefundOutbox.create(환불(결제(2L, 20L)), LocalDateTime.now());
        RefundOutbox succeededOutbox = RefundOutbox.create(환불(결제(3L, 30L)), LocalDateTime.now());

        processingOutbox.markProcessing(LocalDateTime.now());
        succeededOutbox.markProcessing(LocalDateTime.now());
        succeededOutbox.markSucceeded();

        // when
        pendingOutbox.markFailed("대기 실패");
        processingOutbox.markFailed("처리 실패");
        pendingOutbox.markFailed("다시 실패");

        // then
        assertThat(pendingOutbox.getStatus()).isEqualTo(RefundOutboxStatus.FAILED);
        assertThat(pendingOutbox.getLastErrorMessage()).isEqualTo("대기 실패");
        assertThat(processingOutbox.getStatus()).isEqualTo(RefundOutboxStatus.FAILED);
        assertThat(processingOutbox.getProcessingStartedAt()).isNull();
        assertThatThrownBy(() -> succeededOutbox.markFailed("성공 후 실패"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFUND_OUTBOX_STATUS);
    }

    @Test
    void 환불Outbox_실패사유가길면500자로자른다() {
        // given
        RefundOutbox outbox = RefundOutbox.create(환불(결제(1L, 10L)), LocalDateTime.now());

        // when
        outbox.markFailed("x".repeat(600));

        // then
        assertThat(outbox.getLastErrorMessage()).hasSize(500);
    }

    private boolean 재시도(RefundOutbox outbox, String reason, LocalDateTime now) {
        outbox.markProcessing(now);
        return outbox.markRetry(reason, now);
    }

    private Refund 환불(Payment payment) {
        return Refund.createRefund(
                "refund-key-" + payment.getId(),
                "request-hash-" + payment.getId(),
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
