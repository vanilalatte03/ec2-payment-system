package com.teamec2.paymentsystem.domain.payment.scheduler;

import com.teamec2.paymentsystem.domain.payment.repository.PaymentRepository;
import com.teamec2.paymentsystem.domain.payment.service.PaymentConfirmRetryProcessor;
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
public class PaymentRecoveryScheduler {

    private static final int BATCH_SIZE = 20;
    private static final long STALE_PROCESSING_MINUTES = 10L;

    private final PaymentRepository paymentRepository;
    private final PaymentConfirmRetryProcessor paymentConfirmRetryProcessor;

    @Scheduled(fixedDelayString = "${payment.recovery.confirm-fixed-delay-ms:5000}")
    public void processPendingConfirmRetries() {
        List<Long> paymentIds = paymentRepository.findDueConfirmRetryIds(
                LocalDateTime.now(),
                PageRequest.of(0, BATCH_SIZE)
        );

        for (Long paymentId : paymentIds) {
            try {
                paymentConfirmRetryProcessor.process(paymentId);
            } catch (Exception e) {
                log.error("결제 완료 재시도 처리 중 예외 발생. paymentId={}", paymentId, e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${payment.recovery.stale-processing-delay-ms:60000}")
    public void processStaleRecoveryTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_PROCESSING_MINUTES);
        PageRequest pageRequest = PageRequest.of(0, BATCH_SIZE);

        for (Long paymentId : paymentRepository.findStaleConfirmRetryIds(threshold, pageRequest)) {
            try {
                paymentConfirmRetryProcessor.recoverStaleProcessing(paymentId);
            } catch (Exception e) {
                log.error("오래된 결제 완료 재시도 작업 복구 중 예외 발생. paymentId={}", paymentId, e);
            }
        }
    }
}
