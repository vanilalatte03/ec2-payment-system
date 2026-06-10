package com.teamec2.paymentsystem.domain.payment.scheduler;

import com.teamec2.paymentsystem.domain.payment.repository.PaymentCompensationOutboxRepository;
import com.teamec2.paymentsystem.domain.payment.service.PaymentCompensationProcessor;
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
public class PaymentCompensationScheduler {

    private static final int BATCH_SIZE = 20;
    private static final long STALE_PROCESSING_MINUTES = 10L;

    private final PaymentCompensationOutboxRepository paymentCompensationOutboxRepository;
    private final PaymentCompensationProcessor paymentCompensationProcessor;

    @Scheduled(fixedDelayString = "${payment.compensation.fixed-delay-ms:5000}")
    public void processPendingCompensations() {
        List<Long> outboxIds = paymentCompensationOutboxRepository.findDuePendingIds(
                LocalDateTime.now(),
                PageRequest.of(0, BATCH_SIZE)
        );

        for (Long outboxId : outboxIds) {
            try {
                paymentCompensationProcessor.process(outboxId);
            } catch (Exception e) {
                log.error("결제 보상 취소 Outbox 처리 중 예외 발생. outboxId={}", outboxId, e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${payment.compensation.stale-processing-delay-ms:60000}")
    public void processStaleProcessingCompensations() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_PROCESSING_MINUTES);

        List<Long> outboxIds = paymentCompensationOutboxRepository.findStaleProcessingIds(
                threshold,
                PageRequest.of(0, BATCH_SIZE)
        );

        for (Long outboxId : outboxIds) {
            try {
                paymentCompensationProcessor.recoverStaleProcessing(outboxId);
            } catch (Exception e) {
                log.error("오래된 결제 보상 취소 Outbox 복구 중 예외 발생. outboxId={}", outboxId, e);
            }
        }
    }
}
