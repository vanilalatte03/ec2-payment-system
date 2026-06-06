package com.teamec2.paymentsystem.domain.refund.service;

import com.teamec2.paymentsystem.domain.payment.dto.PaymentCancelResponse;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundProcessor {

    private final RefundProcessingTxService refundProcessingTxService;
    private final PaymentGateway paymentGateway;

    /**
     * 일반 PENDING Outbox를 처리합니다.
     *
     * 흐름:
     * 1. DB 트랜잭션 안에서 Outbox를 PROCESSING으로 선점
     * 2. 트랜잭션 밖에서 PG 환불 API 호출
     * 3. 성공/실패/결과 불명확에 따라 다시 DB 트랜잭션으로 상태 반영
     */
    public void process(Long outboxId) {
        RefundCancelCommand command = refundProcessingTxService.start(outboxId)
                .orElse(null);

        if (command == null) {
            return;
        }

        /**
         * PG 환불 금액이 0원인 경우입니다.
         *
         * 예:
         * - 전액 포인트 결제
         * - PG 취소 없이 내부 포인트 복구만 하면 되는 환불
         */
        if (command.pgRefundAmount() == 0L) {
            refundProcessingTxService.complete(outboxId);
            return;
        }

        try {
            PaymentCancelResponse response = paymentGateway.cancelPayment(
                    command.portonePaymentId(),
                    command.pgRefundAmount(),
                    command.currentCancellableAmount(),
                    command.reason(),
                    command.portoneIdempotencyKey()
            );

            if (response.isSucceeded()) {
                refundProcessingTxService.complete(outboxId);
                return;
            }

            refundProcessingTxService.fail(
                    outboxId,
                    "PortOne 취소 실패 상태: " + response.status()
            );
        } catch (RuntimeException e) {
            refundProcessingTxService.retryAsPgResultUnknown(
                    outboxId,
                    e.getMessage()
            );
        }
    }

    /**
     * PROCESSING 고착 상태 처리
     * PROCESSING 상태에 오래 머문 Outbox를 복구합니다.
     * PG_RESULT_UNKNOWN 상태로 전환하고 재시도 대상으로 돌립니다.
     */
    public void recoverStaleProcessing(Long outboxId) {
        refundProcessingTxService.retryAsPgResultUnknown(
                outboxId,
                "PROCESSING 상태가 오래 지속되어 PG 취소 결과를 확정하지 못했습니다."
        );
    }
}