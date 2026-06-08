package com.teamec2.paymentsystem.domain.refund.service;

import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.refund.repository.RefundRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 전체 환불 계산 담당
 * 이번 환불의 전체 금액 스냅샷 계산
 *
 * <p>환불 요청 금액을 기준으로 최종 환불 금액 스냅샷을 계산합니다.
 * 이 클래스는 다음 값을 계산합니다.
 * 1. 적립 포인트 회수 전 사용 포인트 환불 예정 금액
 * 2. 적립 포인트 회수 전 PG 환불 예정 금액
 * 3. 이번 환불에서 회수해야 할 적립 포인트 금액
 * 4. 최종 반환 포인트
 * 5. 최종 PG 환불 금액
 * 6. 포인트 회수/PG 차감 내역
 *
 * <p>실제 Refund 생성, RefundItem 생성, Outbox 저장은 담당하지 않습니다.
 */
@Component
@RequiredArgsConstructor
public class RefundAmountCalculator {
    private final RefundRepository refundRepository;
    private final RefundPointSettlementCalculator refundPointSettlementCalculator;

    /**
     * 이번 환불 요청에 대한 환불 금액과 포인트 정산 금액을 계산합니다.
     * 계산 흐름:
     * 1. 이번 요청이 마지막 환불인지 판단합니다.
     * 2. 적립 포인트 회수 전 원래 환불 예정 금액을 계산합니다.
     * 3. 이번 환불에서 회수해야 하는 적립 포인트 금액을 계산합니다.
     * 4. RefundPointSettlementCalculator에게 실제 포인트 정산 계산을 위임합니다.
     * 5. 계산 결과를 검증한 뒤 RefundAmount로 반환합니다.
     */
    public RefundAmount calculate(
            Payment payment,
            long requestedRefundAmount,
            long totalRemainingRefundableAmount,
            long currentPointBalance
    ) {
        boolean finalRefund = requestedRefundAmount == totalRemainingRefundableAmount;

        long grossPointRefundAmount;
        long grossPgRefundAmount;
        long earnedPointRecoveryAmount;

        if (finalRefund) {
            /*
             * 마지막 환불에서는 비율 계산으로 생긴 버림 오차를 제거하기 위해
             * 결제 당시 전체 금액에서 이미 완료된 gross 환불 금액을 뺀 값을 사용합니다.
             *
             * 여기서 최종 환불액(pointRefundAmount, pgRefundAmount) 합계를 사용하면 안 됩니다.
             * 이전 부분 환불에서 적립 포인트 회수 때문에 최종 환불액이 줄어들 수 있기 때문입니다.
             */
            long completedGrossPointRefundAmount =
                    refundRepository.sumCompletedGrossPointRefundAmount(payment.getId());

            long completedGrossPgRefundAmount =
                    refundRepository.sumCompletedGrossPgRefundAmount(payment.getId());

            long completedEarnedPointRecoveryAmount =
                    refundRepository.sumCompletedEarnedPointRecoveryAmount(payment.getId());

            grossPointRefundAmount = payment.getUsedPointAmount() - completedGrossPointRefundAmount;
            grossPgRefundAmount = payment.getPgAmount() - completedGrossPgRefundAmount;

            /*
             * 마지막 환불에서는 남은 적립 포인트 회수 대상 전체를 잡습니다.
             * 이전 부분 환불에서 이미 회수한 적립 포인트는 제외합니다.
             */
            earnedPointRecoveryAmount =
                    payment.getRewardPointAmount() - completedEarnedPointRecoveryAmount;
        } else {
            /*
             * 마지막 환불이 아닌 경우에는 요청 금액 비율에 따라
             * 사용 포인트 환불 예정액과 PG 환불 예정액을 계산합니다.
             */
            grossPointRefundAmount = calculatePointRefundAmount(payment, requestedRefundAmount);
            grossPgRefundAmount = requestedRefundAmount - grossPointRefundAmount;

            /*
             * 부분 환불에서는 실제 PG 환불 예정 금액에 비례해서
             * 이번 환불에서 회수할 적립 포인트 금액을 계산합니다.
             */
            earnedPointRecoveryAmount = calculateEarnedPointRecoveryAmount(payment, grossPgRefundAmount);
        }

        /*
         * 실제 포인트 정산 계산은 전용 Calculator에게 위임합니다.
         */
        RefundPointSettlementCalculator.RefundPointSettlement settlement =
                refundPointSettlementCalculator.calculate(
                        grossPointRefundAmount,
                        grossPgRefundAmount,
                        earnedPointRecoveryAmount,
                        currentPointBalance
                );

        validateCalculatedAmount(
                requestedRefundAmount,
                settlement.grossPointRefundAmount(),
                settlement.grossPgRefundAmount(),
                settlement.earnedPointRecoveryAmount(),
                settlement.pointRefundAmount(),
                settlement.pgRefundAmount(),
                settlement.recoveredFromUsedPoint(),
                settlement.recoveredFromBalance(),
                settlement.deductedFromPgRefund()
        );

        /*
         * Refund 엔티티의 refundAmount는 고객에게 실제로 환불되는 최종 금액입니다.
         * 즉, 최종 반환 포인트 + 최종 PG 환불 금액입니다.
         */
        long actualRefundAmount = settlement.pointRefundAmount() + settlement.pgRefundAmount();

        if (actualRefundAmount <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        return new RefundAmount(
                requestedRefundAmount,
                actualRefundAmount,
                settlement.grossPointRefundAmount(),
                settlement.grossPgRefundAmount(),
                settlement.earnedPointRecoveryAmount(),
                settlement.pointRefundAmount(),
                settlement.pgRefundAmount(),
                settlement.recoveredFromUsedPoint(),
                settlement.recoveredFromBalance(),
                settlement.deductedFromPgRefund()
        );
    }

    /**
     * 요청 환불 금액 중 사용 포인트로 돌려줘야 할 원래 금액을 계산합니다.
     *
     * 예를 들어 전체 결제 금액 중 20%를 사용 포인트로 결제했다면,
     * 부분 환불 금액에서도 같은 비율만큼 사용 포인트 환불 예정액을 계산합니다.
     */
    private long calculatePointRefundAmount(Payment payment, long refundAmount) {
        if (payment.getTotalAmount() == 0L || payment.getUsedPointAmount() == 0L) {
            return 0L;
        }

        return refundAmount * payment.getUsedPointAmount() / payment.getTotalAmount();
    }

    /**
     * 이번 부분 환불에서 회수해야 할 적립 포인트 금액을 계산합니다.
     *
     * 적립 포인트는 PG 결제 금액을 기준으로 지급되었다고 보고,
     * 이번 환불의 grossPgRefundAmount가 원래 PG 결제 금액에서 차지하는 비율만큼 회수합니다.
     *
     * 마지막 환불에서는 이 메서드를 사용하지 않고,
     * 남은 적립 포인트 회수 대상 전체를 별도로 계산합니다.
     */
    private long calculateEarnedPointRecoveryAmount(Payment payment, long grossPgRefundAmount) {
        if (payment.getPgAmount() == 0L || payment.getRewardPointAmount() == 0L) {
            return 0L;
        }

        return grossPgRefundAmount * payment.getRewardPointAmount() / payment.getPgAmount();
    }

    /**
     * 환불 정산 계산 결과가 정책적으로 일관적인지 검증합니다.
     *
     * 검증 관계:
     * 1. 요청 환불 금액 = 원래 포인트 환불 예정액 + 원래 PG 환불 예정액
     * 2. 원래 포인트 환불 예정액 = 최종 포인트 환불액 + 사용 포인트에서 회수한 금액
     * 3. 원래 PG 환불 예정액 = 최종 PG 환불액 + PG 환불액에서 차감한 금액
     * 4. 회수해야 할 적립 포인트 = 사용 포인트 회수 + 보유 포인트 회수 + PG 환불 차감
     *
     * 주의:
     * recoveredFromBalance는 고객의 기존 보유 포인트에서 차감하는 금액입니다.
     * 따라서 requestedRefundAmount 계산에 직접 더하면 안 됩니다.
     */
    private void validateCalculatedAmount(
            long requestedRefundAmount,
            long grossPointRefundAmount,
            long grossPgRefundAmount,
            long earnedPointRecoveryAmount,
            long pointRefundAmount,
            long pgRefundAmount,
            long recoveredFromUsedPoint,
            long recoveredFromBalance,
            long deductedFromPgRefund
    ) {
        if (requestedRefundAmount <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (grossPointRefundAmount < 0
                || grossPgRefundAmount < 0
                || earnedPointRecoveryAmount < 0
                || pointRefundAmount < 0
                || pgRefundAmount < 0
                || recoveredFromUsedPoint < 0
                || recoveredFromBalance < 0
                || deductedFromPgRefund < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (requestedRefundAmount != grossPointRefundAmount + grossPgRefundAmount) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (grossPointRefundAmount != pointRefundAmount + recoveredFromUsedPoint) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (grossPgRefundAmount != pgRefundAmount + deductedFromPgRefund) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (earnedPointRecoveryAmount
                != recoveredFromUsedPoint + recoveredFromBalance + deductedFromPgRefund) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
