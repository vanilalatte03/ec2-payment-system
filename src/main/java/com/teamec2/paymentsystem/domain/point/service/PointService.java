package com.teamec2.paymentsystem.domain.point.service;

import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.point.entity.PointTransaction;
import com.teamec2.paymentsystem.domain.point.enums.PointTransactionType;
import com.teamec2.paymentsystem.domain.point.repository.PointTransactionRepository;
import com.teamec2.paymentsystem.domain.refund.entity.Refund;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.domain.user.repository.UserRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PointService {

    private final PointTransactionRepository pointTransactionRepository;
    private final UserRepository userRepository;

    /**
     * 결제 완료 후 PG 실결제 금액의 1% 포인트를 적립하고 원장을 기록합니다.
     * 멱등키: PAYMENT:{paymentId}:EARN
     * 멱등 키가 이미 존재하면 중복 적립으로 보고 잔액을 다시 증가시키지 않습니다.
     */
    @Transactional
    public void earnPoints(Payment payment) {
        validateEarnPointRequest(payment);

        Long rewardPointAmount = payment.getRewardPointAmount();

        if (rewardPointAmount == 0L) {
            return;
        }

        User user = findUserForPointUpdate(payment);

        // 결제성 포인트 거래의 멱등 키 생성
        String idempotencyKey = PointTransaction.paymentIdempotencyKey(
                payment,
                PointTransactionType.EARN
        );

        if (pointTransactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }

        user.increasePointBalance(rewardPointAmount);

        PointTransaction pointTransaction = PointTransaction.createForPayment(
                user,
                payment,
                PointTransactionType.EARN,
                rewardPointAmount
        );

        pointTransactionRepository.save(pointTransaction);
    }

    /**
     * 주문 생성 시 사용할 포인트를 예약 차감하고 원장을 기록합니다.
     * 멱등키: PAYMENT:{paymentId}:USE_RESERVE
     * 멱등 키로 같은 결제의 중복 예약 차감을 방지합니다.
     * 예약 방식:
     * 주문 생성 시점에 실제 포인트 잔액에서 먼저 차감합니다.
     * 그래야 같은 포인트를 다른 주문에서 중복 사용할 수 없습니다.
     */
    @Transactional
    public void reserveUsedPoints(Payment payment) {
        validatePayment(payment);

        Long amount = payment.getUsedPointAmount();

        // 포인트를 사용하지 않는 주문이라면 예약을 하지 않습니다.
        if (amount == 0L) {
            return;
        }

        User user = findUserForPointUpdate(payment);

        String reserveKey = PointTransaction.paymentIdempotencyKey(
                payment,
                PointTransactionType.USE_RESERVE
        );

        String useKey = PointTransaction.paymentIdempotencyKey(
                payment,
                PointTransactionType.USE
        );

        /**
         * 이미 예약 차감되었거나 최종 사용 처리된 경우에는 중복 차감하지 않습니다.
         */
        if (pointTransactionRepository.existsByIdempotencyKey(reserveKey)
                || pointTransactionRepository.existsByIdempotencyKey(useKey)) {
            return;
        }

        if (user.getPointBalance() < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINT);
        }

        user.decreasePointBalance(amount);

        PointTransaction pointTransaction = PointTransaction.createForPayment(
                user,
                payment,
                PointTransactionType.USE_RESERVE,
                amount
        );

        pointTransactionRepository.save(pointTransaction);
    }

    /**
     * 결제 성공 시 예약 포인트 원장을 최종 사용 원장으로 확정합니다.
     * 멱등키: PAYMENT:{paymentId}:USE
     * 멱등 키가 이미 있으면 이미 확정된 요청으로 보고 다시 처리하지 않습니다.
     */
    @Transactional
    public void confirmReservedPoints(Payment payment) {
        validatePayment(payment);

        Long amount = payment.getUsedPointAmount();

        if (amount == 0L) {
            return;
        }

        String useKey = PointTransaction.paymentIdempotencyKey(
                payment,
                PointTransactionType.USE
        );

        if (pointTransactionRepository.existsByIdempotencyKey(useKey)) {
            return;
        }

        String reserveKey = PointTransaction.paymentIdempotencyKey(
                payment,
                PointTransactionType.USE_RESERVE
        );

        PointTransaction reservedTransaction = pointTransactionRepository
                .findByIdempotencyKey(reserveKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.POINT_ERROR_EXCEPTION));

        /**
         * 예약 원장을 최종 사용 원장으로 변경합니다.
         * 이미 주문 생성 시점에 잔액은 차감했으므로 재차감은 하지 않습니다.
         */
        reservedTransaction.confirmUse();
    }

    /**
     * 환불 시 결제에 사용했던 포인트를 사용자에게 복구하고 원장을 기록합니다.
     * REFUND:{refundId}:USE_RESTORE 멱등 키로 같은 환불의 중복 복구를 방지합니다.
     */
    @Transactional
    public void restoreUsedPoints(Payment payment, Refund refund, Long restorePointAmount) {

        // 환불 포인트 처리에 필요한 값들이 정상인지 검증합니다.
        validateRefundPointRequest(payment, refund, restorePointAmount);
        validateRefundPaymentMatches(payment, refund);

        // 복구할 포인트가 없으면 잔액 변경과 거래 내역을 만들지 않고 종료합니다.
        if (restorePointAmount == 0L) {return;}

        User user = findUserForPointUpdate(payment);

        String idempotencyKey = PointTransaction.refundIdempotencyKey(
                refund,
                PointTransactionType.USE_RESTORE
        );

        if (pointTransactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }

        // 사용했던 포인트를 환불 시 다시 복구해주므로 잔액을 증가시킵니다.
        user.increasePointBalance(restorePointAmount);

        PointTransaction pointTransaction = PointTransaction.createForRefund(
                user,
                payment,
                refund,
                PointTransactionType.USE_RESTORE,
                restorePointAmount
        );

        pointTransactionRepository.save(pointTransaction);
    }

    /**
     * 주문 취소 또는 결제 실패 시 예약 차감했던 포인트를 복구하고 원장을 기록합니다.
     * 멱등키: PAYMENT:{paymentId}:USE_CANCEL
     * 멱등 키로 같은 결제의 중복 예약 취소를 방지합니다.
     * 사용 시점:
     * 결제가 최종 성공하지 못한 경우
     * 주문 생성 단계에서 예약 차감한 포인트를 되돌려야 하는 경우
     */
    @Transactional
    public void cancelReservedPoints(Payment payment) {
        validatePayment(payment);

        Long amount = payment.getUsedPointAmount();

        if (amount == 0L) {
            return;
        }

        User user = findUserForPointUpdate(payment);

        String cancelKey = PointTransaction.paymentIdempotencyKey(
                payment,
                PointTransactionType.USE_CANCEL
        );

        if (pointTransactionRepository.existsByIdempotencyKey(cancelKey)) {
            return;
        }

        String reserveKey = PointTransaction.paymentIdempotencyKey(
                payment,
                PointTransactionType.USE_RESERVE
        );

        /**
         * 예약 차감 원장이 실제로 존재하는지 확인합니다.
         * 예약 차감이 없는데 취소 복구를 하면 포인트가 부당하게 증가할 수 있습니다.
         */
         pointTransactionRepository.findByIdempotencyKey(reserveKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.POINT_ERROR_EXCEPTION));

        user.increasePointBalance(amount);

        PointTransaction pointTransaction = PointTransaction.createForPayment(
                user,
                payment,
                PointTransactionType.USE_CANCEL,
                amount
        );

        pointTransactionRepository.save(pointTransaction);
    }

    /**
     * 결제 대기 주문을 부분 취소할 때, 예약 차감된 포인트 중 줄어든 금액만 복구합니다.
     * ex)
     * - 주문 생성 시 10,000P 예약 차감
     * - 결제 대기 중 일부 상품 취소로 사용 포인트가 7,000P로 줄어듦
     * - 차액 3,000P만 복구
     */
    @Transactional
    public void restoreReservedPointsForOrderCancel(
            Payment payment,
            Long restorePointAmount,
            List<String> orderCancelKeys
    ) {
        validateOrderCancelPointRequest(payment, restorePointAmount, orderCancelKeys);

        if (restorePointAmount == 0L) {
            return;
        }

        String reserveKey = PointTransaction.paymentIdempotencyKey(
                payment,
                PointTransactionType.USE_RESERVE
        );

        pointTransactionRepository.findByIdempotencyKey(reserveKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.POINT_ERROR_EXCEPTION));

        String cancelKey = PointTransaction.paymentOrderCancelIdempotencyKey(
                payment,
                PointTransactionType.USE_CANCEL,
                orderCancelKeys
        );

        if (pointTransactionRepository.existsByIdempotencyKey(cancelKey)) {
            return;
        }

        User user = findUserForPointUpdate(payment);
        user.increasePointBalance(restorePointAmount);

        PointTransaction pointTransaction = PointTransaction.createForPaymentOrderCancel(
                user,
                payment,
                PointTransactionType.USE_CANCEL,
                restorePointAmount,
                orderCancelKeys
        );

        pointTransactionRepository.save(pointTransaction);
    }

    /**
     * 환불 정산 계산 전에 사용자의 현재 포인트 잔액을 비관적 락으로 조회합니다.
     * 환불 금액을 계산한 직후 다른 요청에서 포인트를 써버리면,
     * 계산한 recoveredFromBalance와 실제 차감 가능한 잔액이 달라질 수 있기 때문입니다.
     */
    @Transactional
    public long getCurrentPointBalanceForUpdate(Payment payment) {
        User user = findUserForPointUpdate(payment);
        return user.getPointBalance();
    }

    /**
     * 환불 요청 시점에 적립 포인트 회수 예정 금액을 미리 차감합니다.
     * 이 메서드는 PG 환불 성공 전이므로 "최종 회수"가 아니라 "예약 회수"입니다.
     * PG 환불이 최종 실패하면 releaseReservedEarnedPointRecovery(...)로 되돌려야 합니다.
     */
    @Transactional
    public void reserveEarnedPointRecoveryFromBalance(
            Payment payment,
            Refund refund,
            Long recoveredFromBalance
    ) {
        validateRefundPointRequest(payment, refund, recoveredFromBalance);
        validateRefundPaymentMatches(payment, refund);

        if (recoveredFromBalance == 0L) {
            return;
        }

        User user = findUserForPointUpdate(payment);

        String idempotencyKey = PointTransaction.refundIdempotencyKey(
                refund,
                PointTransactionType.EARN_RECOVERY_RESERVE
        );

        if (pointTransactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }

        user.decreasePointBalance(recoveredFromBalance);

        PointTransaction pointTransaction = PointTransaction.createForRefund(
                user,
                payment,
                refund,
                PointTransactionType.EARN_RECOVERY_RESERVE,
                recoveredFromBalance
        );

        pointTransactionRepository.save(pointTransaction);
    }

    /**
     * PG 환불이 최종 실패했을 때,
     * 환불 요청 시점에 예약 차감했던 적립 포인트를 다시 돌려줍니다.
     */
    @Transactional
    public void releaseReservedEarnedPointRecovery(
            Payment payment,
            Refund refund
    ) {
        validateRefundPointRequest(payment, refund, refund.getRecoveredFromBalance());
        validateRefundPaymentMatches(payment, refund);

        Long recoveredFromBalance = refund.getRecoveredFromBalance();

        if (recoveredFromBalance == 0L) {
            return;
        }

        User user = findUserForPointUpdate(payment);

        String releaseKey = PointTransaction.refundIdempotencyKey(
                refund,
                PointTransactionType.EARN_RECOVERY_RELEASE
        );

        if (pointTransactionRepository.existsByIdempotencyKey(releaseKey)) {
            return;
        }

        String reserveKey = PointTransaction.refundIdempotencyKey(
                refund,
                PointTransactionType.EARN_RECOVERY_RESERVE
        );

        pointTransactionRepository.findByIdempotencyKey(reserveKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.POINT_ERROR_EXCEPTION));

        user.increasePointBalance(recoveredFromBalance);

        PointTransaction pointTransaction = PointTransaction.createForRefund(
                user,
                payment,
                refund,
                PointTransactionType.EARN_RECOVERY_RELEASE,
                recoveredFromBalance
        );

        pointTransactionRepository.save(pointTransaction);
    }

    /**
     * 환불 시 고객에게 실제 반환할 사용 포인트를 복구합니다.
     * 멱등 키: REFUND:{refundId}:USE_RESTORE
     * 같은 환불 건에 대해 이미 USE_RESTORE 원장이 있으면 중복 증가하지 않습니다.
     */
    private void restoreRefundUsedPoint(
            User user,
            Payment payment,
            Refund refund,
            Long pointRefundAmount
    ) {
        String idempotencyKey = PointTransaction.refundIdempotencyKey(
                refund,
                PointTransactionType.USE_RESTORE
        );

        if (pointTransactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }

        user.increasePointBalance(pointRefundAmount);

        PointTransaction pointTransaction = PointTransaction.createForRefund(
                user,
                payment,
                refund,
                PointTransactionType.USE_RESTORE,
                pointRefundAmount
        );

        pointTransactionRepository.save(pointTransaction);
    }

    /**
     * 결제 완료 후 포인트 적립 요청이 유효한지 검증합니다.
     */
    private void validateEarnPointRequest(Payment payment) {

        if (payment == null
                || payment.getId() == null
                || payment.getRewardPointAmount() == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (payment.getRewardPointAmount() < 0) {
            throw new BusinessException(ErrorCode.INVALID_POINT_TRANSACTION_AMOUNT);
        }
    }

    /**
     * 결제 기반 포인트 처리에 필요한 Payment 값이 유효한지 검증합니다.
     */
    private void validatePayment(Payment payment) {
        if (payment == null
                || payment.getId() == null
                || payment.getUsedPointAmount() == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (payment.getUsedPointAmount() < 0) {
            throw new BusinessException(ErrorCode.INVALID_POINT_TRANSACTION_AMOUNT);
        }
    }

    /**
     * 사용자 포인트 잔액 변경을 위해 User를 비관적 락으로 조회합니다.
     * 같은 사용자의 포인트 잔액을 동시에 변경하는 요청이 여러 개 들어와도 잔액 계산이 꼬이지 않도록 하기 위한 목적입니다.
     */
    private User findUserForPointUpdate(Payment payment) {
        if (payment.getOrder() == null
                || payment.getOrder().getUser() == null
                || payment.getOrder().getUser().getId() == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        Long userId = payment.getOrder().getUser().getId();

        return userRepository.findByIdForUpdate(userId).orElseThrow(
                () -> new BusinessException(ErrorCode.USER_NOT_FOUND)
        );
    }

    /**
     * 환불 포인트 정산 처리에 필요한 Payment/Refund 스냅샷 값이 유효한지 검증합니다.
     * 금액을 다시 계산하지 않습니다.
     * RefundService에서 확정한 스냅샷 값이 존재하고 음수가 아닌지만 확인합니다.
     */
    private void validateRefundPointSettlementRequest(
            Payment payment,
            Refund refund
    ) {
        if (payment == null
                || payment.getId() == null
                || refund == null
                || refund.getId() == null
                || refund.getPointRefundAmount() == null
                || refund.getRecoveredFromBalance() == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        /**
         * 잘못된 Payment와 Refund 조합으로 포인트 잔액이 변경되는 것을 막습니다.
         */
        if (refund.getPayment() == null
                || refund.getPayment().getId() == null
                || !refund.getPayment().getId().equals(payment.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (refund.getPointRefundAmount() < 0
                || refund.getRecoveredFromBalance() < 0) {
            throw new BusinessException(ErrorCode.INVALID_POINT_TRANSACTION_AMOUNT);
        }
    }

    private void validateRefundPointRequest(Payment payment, Refund refund, Long amount) {

        if (payment == null || refund == null || amount == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        // 포인트 정책 상, 포인트 금액은 음수가 될 수 없습니다.
        // 환불 시 복구할 포인트 = 0 도 정상 상황일 수 있으므로 amount < 0 으로 처리합니다.
        if (amount < 0) {
            throw new BusinessException(ErrorCode.INVALID_POINT_TRANSACTION_AMOUNT);
        }
    }

    /**
     * 결제 대기 주문 부분 취소 시 포인트 복구 요청값을 검증합니다.
     */
    private void validateOrderCancelPointRequest(Payment payment, Long amount, List<String> orderCancelKeys) {
        if (payment == null
                || payment.getId() == null
                || amount == null
                || orderCancelKeys == null
                || orderCancelKeys.isEmpty()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (amount < 0) {
            throw new BusinessException(ErrorCode.INVALID_POINT_TRANSACTION_AMOUNT);
        }
    }

    private void validateRefundPaymentMatches(Payment payment, Refund refund) {
        if (payment == null
                || payment.getId() == null
                || refund == null
                || refund.getPayment() == null
                || refund.getPayment().getId() == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        /*
         * 다른 결제의 Refund를 잘못 넘기면 엉뚱한 사용자의 포인트가 변경될 수 있습니다.
         * 환불 포인트 처리는 반드시 Refund가 연결된 Payment 기준으로만 처리해야 합니다.
         */
        if (!refund.getPayment().getId().equals(payment.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
