package com.teamec2.paymentsystem.domain.refund.service;

import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.springframework.stereotype.Component;


/**
 * 포인트 정산 계산 담당
 * 포인트 회수 후 최종 포인트/PG 환불액 계산
 */
@Component
public class RefundPointSettlementCalculator {
    /**
     * 환불 시 포인트 반환, 적립 포인트 회수, PG 환불 차감 금액을 계산합니다.
     *
     * @param grossPointRefundAmount 환불 시 원래 고객에게 반환해야 할 사용 포인트
     * @param grossPgRefundAmount 포인트 회수 전 원래 PG 환불 예정 금액
     * @param earnedPointRecoveryAmount 환불로 인해 회수해야 할 적립 포인트
     * @param currentPointBalance 환불 처리 전 고객의 현재 보유 포인트 잔액
     * @return 포인트 정산 계산 결과
     */
    public RefundPointSettlement calculate(
            long grossPointRefundAmount,
            long grossPgRefundAmount,
            long earnedPointRecoveryAmount,
            long currentPointBalance
    ) {
        // 입력 금액은 모두 0 이상이어야 합니다.
        validateAmount(grossPointRefundAmount);
        validateAmount(grossPgRefundAmount);
        validateAmount(earnedPointRecoveryAmount);
        validateAmount(currentPointBalance);

        /*
         * 1. 반환해야 할 사용 포인트에서 회수 대상 적립 포인트를 먼저 차감합니다.
         * ex)
         * 반환 대상 사용 포인트: 1,000P
         * 회수 대상 적립 포인트: 100P
         * → 사용 포인트에서 100P 회수
         */
        long recoveredFromUsedPoint = Math.min(grossPointRefundAmount, earnedPointRecoveryAmount);

        /*
         * 2. 사용 포인트에서 적립 포인트를 회수하고 남은 포인트만 고객에게 반환합니다.
         * ex)
         * 반환 대상 사용 포인트: 1,000P
         * 사용 포인트에서 회수: 100P
         * → 실제 반환 포인트: 900P
         */
        long actualPointRefundAmount = grossPointRefundAmount - recoveredFromUsedPoint;

        /*
         * 3. 사용 포인트에서 회수하고도 아직 남은 회수 대상 적립 포인트를 계산합니다.
         * ex)
         * 회수 대상 적립 포인트: 100P
         * 사용 포인트에서 회수: 50P
         * → 남은 회수 대상: 50P
         */
        long remainingRecoveryAmount = earnedPointRecoveryAmount - recoveredFromUsedPoint;

        /*
         * 4. 남은 회수 대상 적립 포인트를 고객의 현재 보유 포인트(point_balance)에서 차감합니다.
         *
         * 단, 현재 보유 포인트보다 많이 차감할 수 없으므로
         * 현재 보유 포인트와 남은 회수 대상 중 더 작은 값을 회수합니다.
         */
        long recoveredFromBalance = Math.min(currentPointBalance, remainingRecoveryAmount);

        /*
         * 5. 사용 포인트와 현재 보유 포인트로도 회수하지 못한 금액을 계산합니다.
         * 이 금액은 최후의 수단으로 PG 환불 금액에서 차감됩니다.
         */
        long deductedFromPgRefund = remainingRecoveryAmount - recoveredFromBalance;

        /*
         * 6. 최종 PG 환불 금액을 계산합니다.
         * 최종 PG 환불 금액 = 원래 PG 환불 예정 금액 - 포인트 회수 부족분
         */
        long actualPgRefundAmount = grossPgRefundAmount - deductedFromPgRefund;

        /*
         * 7. 최종 PG 환불 금액은 음수가 될 수 없습니다. (방어코드)
         *
         * 현재 정책상 회수 대상 적립 포인트는 환불 대상 실제 결제 금액을 초과하지 않아야 하므로,
         * 이 조건은 정상적인 사용자 요청에서는 발생하면 안 됩니다.
         *
         * 만약 음수가 발생한다면 부분 환불 배분, 적립 포인트 회수 금액,
         * PG 환불 예정 금액 중 하나가 잘못 계산된 상태로 보고 처리를 중단합니다.
         */
        if (actualPgRefundAmount < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        return new RefundPointSettlement(
                grossPointRefundAmount,
                grossPgRefundAmount,
                earnedPointRecoveryAmount,
                actualPointRefundAmount,
                actualPgRefundAmount,
                recoveredFromUsedPoint,
                recoveredFromBalance,
                deductedFromPgRefund
        );
    }

    /**
     * 금액 값이 음수인지 검증합니다.
     * 포인트 금액과 PG 환불 금액은 모두 0 이상이어야 합니다.
     */
    private void validateAmount(long amount) {
        if (amount < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    /**
     * 환불 포인트 정산 계산 결과입니다.
     *
     * @param grossPointRefundAmount 회수 전 원래 반환 대상 사용 포인트
     * @param grossPgRefundAmount 회수 전 원래 PG 환불 예정 금액
     * @param earnedPointRecoveryAmount 회수해야 할 적립 포인트
     * @param pointRefundAmount 최종 반환 포인트
     * @param pgRefundAmount 최종 PG 환불 금액
     * @param recoveredFromUsedPoint 반환 대상 사용 포인트에서 회수한 적립 포인트
     * @param recoveredFromBalance 현재 보유 포인트에서 회수한 적립 포인트
     * @param deductedFromPgRefund PG 환불 금액에서 차감한 금액
     */
    public record RefundPointSettlement(
            long grossPointRefundAmount,
            long grossPgRefundAmount,
            long earnedPointRecoveryAmount,
            long pointRefundAmount,
            long pgRefundAmount,
            long recoveredFromUsedPoint,
            long recoveredFromBalance,
            long deductedFromPgRefund
    ) {
    }
}
