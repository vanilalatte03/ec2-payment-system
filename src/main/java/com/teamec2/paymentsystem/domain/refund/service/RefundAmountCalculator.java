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

        /*
         * PG 우선 환불 정책에서는 부분 환불에서도 이미 완료된 gross 환불 누적액을 조회해야 합니다.
         * 그래야 PG 환불 가능 금액을 먼저 소진하고, 부족한 금액만 사용 포인트 반환으로 넘길 수 있습니다.
         */
        long completedGrossPointRefundAmount =
                refundRepository.sumCompletedGrossPointRefundAmount(payment.getId());

        long completedGrossPgRefundAmount =
                refundRepository.sumCompletedGrossPgRefundAmount(payment.getId());

        long completedEarnedPointRecoveryAmount =
                refundRepository.sumCompletedEarnedPointRecoveryAmount(payment.getId());

        /*
         * 아직 반환 가능한 사용 포인트, 아직 PG로 환불 가능한 금액,
         * 아직 회수해야 하는 적립 포인트 잔여량을 계산합니다.
         */
        long remainingGrossPointRefundAmount =
                payment.getUsedPointAmount() - completedGrossPointRefundAmount;

        long remainingGrossPgRefundAmount =
                payment.getPgAmount() - completedGrossPgRefundAmount;

        long remainingEarnedPointRecoveryAmount =
                payment.getRewardPointAmount() - completedEarnedPointRecoveryAmount;

        validateRemainingAmount(remainingGrossPointRefundAmount);
        validateRemainingAmount(remainingGrossPgRefundAmount);
        validateRemainingAmount(remainingEarnedPointRecoveryAmount);

        if (finalRefund) {
            // 마지막 환불에서는 남아 있는 PG 환불 가능 금액과 남아 있는 사용 포인트 반환 가능 금액을 모두 정리합니다.
            grossPgRefundAmount = remainingGrossPgRefundAmount;
            grossPointRefundAmount = remainingGrossPointRefundAmount;

            /*
             * 마지막 환불에서는 정수 나눗셈으로 생긴 버림 오차를 제거하기 위해
             * 남은 적립 포인트 회수 대상 전체를 잡습니다.
             */
            earnedPointRecoveryAmount = remainingEarnedPointRecoveryAmount;
        } else {
            /*
             * 부분환불에서는 PG 환불 가능 금액을 먼저 사용합니다.
             * 사용 포인트는 PG 환불 가능 금액이 부족한 경우에만 반환 대상으로 잡습니다.
             */
            grossPgRefundAmount = calculatePgRefundAmount(
                    requestedRefundAmount,
                    remainingGrossPgRefundAmount
            );

            /*
             * PG로 환불하지 못한 나머지 금액만 사용 포인트 반환 대상으로 계산합니다.
             */
            long remainingRefundAmountAfterPg =
                    requestedRefundAmount - grossPgRefundAmount;

            grossPointRefundAmount = calculatePointRefundAmount(
                    remainingRefundAmountAfterPg,
                    remainingGrossPointRefundAmount
            );

            /*
             * 적립 포인트 회수액은 PG 환불액 기준이 아니라
             * 환불 대상 상품 금액(requestedRefundAmount)이 전체 주문 금액에서 차지하는 비율로 계산합니다.
             */
            earnedPointRecoveryAmount = calculateEarnedPointRecoveryAmount(
                    payment,
                    requestedRefundAmount,
                    remainingEarnedPointRecoveryAmount
            );
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
     * PG 환불 이후 남은 환불 대상 금액 중 사용 포인트로 반환할 금액을 계산합니다.
     * 단, 남아 있는 사용 포인트 반환 가능 금액을 초과할 수 없습니다.
     */
    private long calculatePointRefundAmount(
            long remainingRefundAmountAfterPg,
            long remainingGrossPointRefundAmount
    ) {
        return Math.min(remainingRefundAmountAfterPg, remainingGrossPointRefundAmount);
    }

    /**
     * 요청 환불 금액 중 PG로 먼저 환불할 금액을 계산합니다.
     * 단, 남아 있는 PG 환불 가능 금액을 초과할 수 없습니다.
     */
    private long calculatePgRefundAmount(
            long requestedRefundAmount,
            long remainingGrossPgRefundAmount
    ) {
        return Math.min(requestedRefundAmount, remainingGrossPgRefundAmount);
    }

    /**
     * 이번 부분 환불에서 회수해야 할 적립 포인트 금액을 계산합니다.
     * 적립 포인트는 PG 환불 금액 기준이 아니라,
     * 전체 주문 금액 대비 이번 환불 대상 상품 금액 비율로 회수합니다.
     * 마지막 환불에서는 이 메서드를 사용하지 않고,
     * 남은 적립 포인트 회수 대상 전체를 별도로 계산합니다.
     */
    private long calculateEarnedPointRecoveryAmount(
            Payment payment,
            long requestedRefundAmount,
            long remainingEarnedPointRecoveryAmount
    ) {
        if (payment.getTotalAmount() == 0L || payment.getRewardPointAmount() == 0L) {
            return 0L;
        }

        long calculatedRecoveryAmount =
                requestedRefundAmount * payment.getRewardPointAmount() / payment.getTotalAmount();

        return Math.min(calculatedRecoveryAmount, remainingEarnedPointRecoveryAmount);
    }

    /**
     * 완료 환불 누적값을 뺀 잔여 한도가 음수인지 검증합니다.
     */
    private void validateRemainingAmount(long amount) {
        if (amount < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    /**
     * 환불 정산 계산 결과가 정책적으로 일관적인지 검증합니다.
     * 검증 관계:
     * 1. 요청 환불 금액 = 원래 포인트 환불 예정액 + 원래 PG 환불 예정액
     * 2. 원래 포인트 환불 예정액 = 최종 포인트 환불액 + 사용 포인트에서 회수한 금액
     * 3. 원래 PG 환불 예정액 = 최종 PG 환불액 + PG 환불액에서 차감한 금액
     * 4. 회수해야 할 적립 포인트 = 사용 포인트 회수 + 보유 포인트 회수 + PG 환불 차감
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