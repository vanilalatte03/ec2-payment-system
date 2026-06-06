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

        PaymentCancelResponse response;

        try {
            response = paymentGateway.cancelPayment(
                    command.portonePaymentId(),
                    command.pgRefundAmount(),
                    command.currentCancellableAmount(),
                    command.reason(),
                    command.portoneIdempotencyKey()
            );
        } catch (RuntimeException e) {
            /**
             * 외부 PG 호출 중 예외가 발생한 경우입니다.
             * 이 경우 실제로 PortOne에서 취소가 성공했는지 실패했는지 알 수 없으므로
             * 실패로 확정하지 않고 PG_RESULT_UNKNOWN + 재시도 대상으로 처리합니다.
             */
            refundProcessingTxService.retryAsPgResultUnknown(
                    outboxId,
                    e.getMessage()
            );

            return;
        }

        handleCancelResponse(outboxId, response);
    }

    /**
     * 외부 결제 취소 응답(rawStatus)을 우리 서비스 기준 상태(cancelStatus)에 따라 처리합니다.
     * SUCCEEDED:
     * - PG 취소 성공이므로 내부 DB 환불 완료 처리
     * RESULT_UNKNOWN:
     * - PortOne 원본 상태가 REQUESTED이거나 알 수 없는 상태인 경우
     * - 실패로 확정하지 않고 PG_RESULT_UNKNOWN + 재시도 대상으로 처리
     * FAILED:
     * - 명확한 실패 상태이므로 환불 실패 처리
     */
    private void handleCancelResponse(Long outboxId, PaymentCancelResponse response) {
        if (response.isSucceeded()) {
            refundProcessingTxService.complete(outboxId);
            return;
        }

        if (response.isResultUnknown()) {
            refundProcessingTxService.retryAsPgResultUnknown(
                    outboxId,
                    "PortOne 취소 결과 미확정 상태: " + response.rawStatus()
            );

            return;
        }

        if (response.isFailed()) {
            refundProcessingTxService.fail(
                    outboxId,
                    "PortOne 취소 실패 상태: " + response.rawStatus()
            );

            return;
        }

        /**
         * 결제/환불 도메인에서는 알 수 없는 상태를 실패로 확정하지 않고
         * 결과 미확정으로 보냅니다.
         */
        refundProcessingTxService.retryAsPgResultUnknown(
                outboxId,
                "PortOne 취소 응답 상태를 해석할 수 없습니다. status=" + response.rawStatus()
        );
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