package com.teamec2.paymentsystem.domain.refund.repository;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.refund.entity.Refund;
import com.teamec2.paymentsystem.domain.refund.entity.RefundOutbox;
import com.teamec2.paymentsystem.domain.refund.enums.RefundOutboxStatus;
import com.teamec2.paymentsystem.domain.refund.enums.RefundStatus;
import com.teamec2.paymentsystem.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RefundOutboxRepositoryTest {

    @Autowired
    RefundOutboxRepository refundOutboxRepository;

    @Autowired
    RefundRepository refundRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void 처리대기Outbox조회_대기상태이고시도시각이지난ID만_ID오름차순으로조회한다() {
        // given
        Payment payment = 결제_저장();
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 12, 0);
        RefundOutbox dueOutbox = 환불Outbox_저장(
                payment,
                "due",
                null,
                RefundStatus.PROCESSING,
                RefundOutboxStatus.PENDING,
                now.minusMinutes(1),
                null
        );
        환불Outbox_저장(
                payment,
                "future",
                null,
                RefundStatus.PROCESSING,
                RefundOutboxStatus.PENDING,
                now.plusMinutes(1),
                null
        );
        환불Outbox_저장(
                payment,
                "failed",
                null,
                RefundStatus.PROCESSING,
                RefundOutboxStatus.FAILED,
                now.minusMinutes(1),
                null
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<Long> outboxIds = refundOutboxRepository.findDuePendingIds(now, PageRequest.of(0, 20));

        // then
        assertThat(outboxIds).containsExactly(dueOutbox.getId());
    }

    @Test
    void 오래된ProcessingOutbox조회_임계시각이전ProcessingID만_ID오름차순으로조회한다() {
        // given
        Payment payment = 결제_저장();
        LocalDateTime threshold = LocalDateTime.of(2026, 6, 1, 12, 0);
        RefundOutbox staleOutbox = 환불Outbox_저장(
                payment,
                "stale",
                null,
                RefundStatus.PROCESSING,
                RefundOutboxStatus.PROCESSING,
                threshold.minusMinutes(20),
                threshold.minusMinutes(11)
        );
        환불Outbox_저장(
                payment,
                "fresh",
                null,
                RefundStatus.PROCESSING,
                RefundOutboxStatus.PROCESSING,
                threshold.minusMinutes(20),
                threshold.plusSeconds(1)
        );
        환불Outbox_저장(
                payment,
                "pending-old",
                null,
                RefundStatus.PROCESSING,
                RefundOutboxStatus.PENDING,
                threshold.minusMinutes(20),
                null
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<Long> outboxIds = refundOutboxRepository.findStaleProcessingIds(threshold, PageRequest.of(0, 20));

        // then
        assertThat(outboxIds).containsExactly(staleOutbox.getId());
    }

    @Test
    void 취소ID조회_처리가능한환불과Outbox상태만조회한다() {
        // given
        Payment payment = 결제_저장();
        RefundOutbox processableOutbox = 환불Outbox_저장(
                payment,
                "processable",
                "cancel-processable",
                RefundStatus.PROCESSING,
                RefundOutboxStatus.PENDING,
                LocalDateTime.now(),
                null
        );
        환불Outbox_저장(
                payment,
                "completed",
                "cancel-completed",
                RefundStatus.COMPLETED,
                RefundOutboxStatus.SUCCEEDED,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<RefundOutbox> foundOutbox =
                refundOutboxRepository.findProcessableByPortoneCancellationIdForUpdate(
                        payment.getPortonePaymentId(),
                        "cancel-processable"
                );
        Optional<RefundOutbox> completedOutbox =
                refundOutboxRepository.findProcessableByPortoneCancellationIdForUpdate(
                        payment.getPortonePaymentId(),
                        "cancel-completed"
                );

        // then
        assertThat(foundOutbox).isPresent();
        assertThat(foundOutbox.get().getId()).isEqualTo(processableOutbox.getId());
        assertThat(completedOutbox).isEmpty();
    }

    @Test
    void 취소ID조회_재시도초과실패Outbox라도_PG미확정환불이면복구대상으로조회한다() {
        // given
        Payment payment = 결제_저장();
        RefundOutbox failedOutbox = 환불Outbox_저장(
                payment,
                "recoverable",
                "cancel-recoverable",
                RefundStatus.PG_RESULT_UNKNOWN,
                RefundOutboxStatus.FAILED,
                LocalDateTime.now(),
                null
        );

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<RefundOutbox> foundOutbox =
                refundOutboxRepository.findRecoverableFailedByPortoneCancellationIdForUpdate(
                        payment.getPortonePaymentId(),
                        "cancel-recoverable"
                );

        // then
        assertThat(foundOutbox).isPresent();
        assertThat(foundOutbox.get().getId()).isEqualTo(failedOutbox.getId());
    }

    @Test
    void 미식별취소웹훅후보조회_취소ID가없는처리중환불만_ID오름차순으로조회한다() {
        // given
        Payment payment = 결제_저장();
        RefundOutbox firstCandidate = 환불Outbox_저장(
                payment,
                "candidate-1",
                null,
                RefundStatus.PROCESSING,
                RefundOutboxStatus.PENDING,
                LocalDateTime.now(),
                null
        );
        RefundOutbox secondCandidate = 환불Outbox_저장(
                payment,
                "candidate-2",
                null,
                RefundStatus.PG_RESULT_UNKNOWN,
                RefundOutboxStatus.FAILED,
                LocalDateTime.now(),
                null
        );
        환불Outbox_저장(
                payment,
                "has-cancel-id",
                "cancel-known",
                RefundStatus.PROCESSING,
                RefundOutboxStatus.PENDING,
                LocalDateTime.now(),
                null
        );
        환불Outbox_저장(
                payment,
                "completed-without-cancel",
                null,
                RefundStatus.COMPLETED,
                RefundOutboxStatus.SUCCEEDED,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<RefundOutbox> candidates =
                refundOutboxRepository.findUnidentifiedWebhookCandidatesForUpdate(payment.getPortonePaymentId());

        // then
        assertThat(candidates)
                .extracting(RefundOutbox::getId)
                .containsExactly(firstCandidate.getId(), secondCandidate.getId());
    }

    private Payment 결제_저장() {
        User user = User.create(uniqueEmail(), "Password123!", "홍길동", "010-1234-5678");
        entityManager.persist(user);

        Order order = Order.create(user, "ORDER-" + UUID.randomUUID(), 10_000L, 1_000L);
        entityManager.persist(order);

        Payment payment = Payment.createPending(order, 10_000L, 1_000L, 9_000L, 90L);
        entityManager.persist(payment);

        return payment;
    }

    private RefundOutbox 환불Outbox_저장(
            Payment payment,
            String idempotencyKey,
            String cancellationId,
            RefundStatus refundStatus,
            RefundOutboxStatus outboxStatus,
            LocalDateTime nextAttemptAt,
            LocalDateTime processingStartedAt
    ) {
        Refund refund = Refund.createRefund(
                idempotencyKey,
                "a".repeat(64),
                payment.getOrder(),
                payment,
                "refund",
                1_000L,
                0L,
                1_000L,
                0L,
                1_000L,
                0L,
                0L,
                0L,
                0L
        );

        if (cancellationId != null) {
            refund.recordPortoneCancellationId(cancellationId);
        }
        if (refundStatus == RefundStatus.PG_RESULT_UNKNOWN) {
            refund.markPgResultUnknown("unknown");
        }
        if (refundStatus == RefundStatus.FAILED) {
            refund.fail("failed");
        }
        if (refundStatus == RefundStatus.COMPLETED) {
            refund.complete(LocalDateTime.now());
        }

        refundRepository.save(refund);

        RefundOutbox outbox = RefundOutbox.create(refund, nextAttemptAt);
        if (outboxStatus == RefundOutboxStatus.PROCESSING) {
            outbox.markProcessing(processingStartedAt);
        }
        if (outboxStatus == RefundOutboxStatus.FAILED) {
            outbox.markFailed("failed");
        }
        if (outboxStatus == RefundOutboxStatus.SUCCEEDED) {
            outbox.markProcessing(processingStartedAt);
            outbox.markSucceeded();
        }

        return refundOutboxRepository.save(outbox);
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }
}
