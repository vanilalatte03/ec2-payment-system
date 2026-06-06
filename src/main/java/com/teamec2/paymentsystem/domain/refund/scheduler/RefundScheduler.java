package com.teamec2.paymentsystem.domain.refund.scheduler;

import com.teamec2.paymentsystem.domain.refund.repository.RefundOutboxRepository;
import com.teamec2.paymentsystem.domain.refund.service.RefundProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundScheduler {

    private final RefundOutboxRepository refundOutboxRepository;
    private final RefundProcessor refundProcessor;

    private static final int BATCH_SIZE = 20;
    private static final long STALE_PROCESSING_MINUTES = 10L;

    /**
     * 처리 가능한 PENDING Outbox를 주기적으로 처리합니다.
     */
    @Scheduled(fixedDelayString = "${refund.scheduler.fixed-delay-ms:5000}")
    public void processPendingRefunds() {
        List<Long> outboxIds = refundOutboxRepository.findDuePendingIds(
                LocalDateTime.now(),
                PageRequest.of(0, BATCH_SIZE)
        );

        for (Long outboxId : outboxIds) {
            try {
                refundProcessor.process(outboxId);
            } catch (Exception e) {
                log.error("환불 아웃박스 처리 중 예외 발생. outboxId={}", outboxId, e);
            }
        }
    }

    /**
     * PROCESSING 고착 상태 처리
     * PROCESSING 상태에 오래 머문 Outbox를 복구 대상으로 처리합니다.
     *
     * stale processing 작업은 PG 요청이 실제로 전송되었을 수도 있으므로,
     * 단순히 새 요청처럼 취급하면 중복 환불 위험이 있습니다.
     *
     * 현재 구조에서는 같은 refundId 기반 PortOne idempotency key를 사용하므로,
     * 재처리 시에도 같은 멱등 키로 처리되도록 RefundProcessor 쪽에서 관리합니다.
     */
    @Scheduled(fixedDelayString = "${refund.scheduler.stale-processing-delay-ms:60000}")
    public void processStaleProcessingRefunds() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_PROCESSING_MINUTES);

        List<Long> outboxIds = refundOutboxRepository.findStaleProcessingIds(
                threshold,
                PageRequest.of(0, BATCH_SIZE)
        );

        for (Long outboxId : outboxIds) {
            try {
                refundProcessor.recoverStaleProcessing(outboxId);
            } catch (Exception e) {
                log.error("오래된 PROCESSING 환불 아웃박스 복구 중 예외 발생. outboxId={}", outboxId, e);
            }
        }
    }
}