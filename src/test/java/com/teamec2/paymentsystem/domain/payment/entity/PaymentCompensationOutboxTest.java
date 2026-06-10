package com.teamec2.paymentsystem.domain.payment.entity;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.payment.enums.PaymentCompensationOutboxStatus;
import com.teamec2.paymentsystem.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentCompensationOutboxTest {

    @Test
    void 보상취소아웃박스를_생성하면_PENDING상태와처리시각을저장한다() {
        // given
        Payment payment = 결제_생성();
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 12, 30);

        // when
        PaymentCompensationOutbox outbox =
                PaymentCompensationOutbox.create(payment, 800L, "금액 불일치", now);

        // then
        assertThat(outbox.getPayment()).isEqualTo(payment);
        assertThat(outbox.getCancelAmount()).isEqualTo(800L);
        assertThat(outbox.getStatus()).isEqualTo(PaymentCompensationOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isZero();
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now);
        assertThat(outbox.getLastErrorMessage()).isEqualTo("금액 불일치");
    }

    @Test
    void 처리중아웃박스를_재시도예약하면_PENDING으로돌리고_다음시도시각을늦춘다() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 12, 30);
        PaymentCompensationOutbox outbox =
                PaymentCompensationOutbox.create(결제_생성(), 800L, "금액 불일치", now);
        outbox.markProcessing(now.plusSeconds(1));

        // when
        boolean retryScheduled = outbox.markRetry("PG 결과 미확정", now.plusMinutes(1));

        // then
        assertThat(retryScheduled).isTrue();
        assertThat(outbox.getStatus()).isEqualTo(PaymentCompensationOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isAfter(now.plusMinutes(1));
        assertThat(outbox.getProcessingStartedAt()).isNull();
    }

    @Test
    void 처리중아웃박스를_대기상태로표시해도_PROCESSING을유지한다() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 12, 30);
        LocalDateTime processingStartedAt = now.plusSeconds(1);
        PaymentCompensationOutbox outbox =
                PaymentCompensationOutbox.create(결제_생성(), 800L, "금액 불일치", now);
        outbox.markProcessing(processingStartedAt);

        // when
        outbox.markPending("중복 보상 요청", now.plusMinutes(1));

        // then
        assertThat(outbox.getStatus()).isEqualTo(PaymentCompensationOutboxStatus.PROCESSING);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now);
        assertThat(outbox.getProcessingStartedAt()).isEqualTo(processingStartedAt);
        assertThat(outbox.getLastErrorMessage()).isEqualTo("금액 불일치");
    }

    @Test
    void 처리중아웃박스를_성공처리하면_SUCCEEDED가된다() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 12, 30);
        PaymentCompensationOutbox outbox =
                PaymentCompensationOutbox.create(결제_생성(), 800L, "금액 불일치", now);
        outbox.markProcessing(now.plusSeconds(1));

        // when
        outbox.recordPortoneCancellationId("cancel_123");
        outbox.markSucceeded();

        // then
        assertThat(outbox.getStatus()).isEqualTo(PaymentCompensationOutboxStatus.SUCCEEDED);
        assertThat(outbox.getPortoneCancellationId()).isEqualTo("cancel_123");
        assertThat(outbox.getProcessingStartedAt()).isNull();
    }

    private Payment 결제_생성() {
        return Payment.createPending(주문_생성(), 1000L, 200L, 800L, 8L);
    }

    private Order 주문_생성() {
        return Order.create(
                User.create("test@example.com", "Password123!", "홍길동", "010-1234-5678"),
                "ORDER-001",
                1000L,
                200L
        );
    }
}
