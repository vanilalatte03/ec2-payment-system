package com.teamec2.paymentsystem.domain.point.service;

import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.point.dto.PointTransactionResponse;
import com.teamec2.paymentsystem.domain.point.entity.PointTransaction;
import com.teamec2.paymentsystem.domain.point.enums.PointTransactionType;
import com.teamec2.paymentsystem.domain.point.repository.PointTransactionRepository;
import com.teamec2.paymentsystem.domain.refund.entity.Refund;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.domain.user.repository.UserRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import com.teamec2.paymentsystem.global.pagination.PageResponse;
import com.teamec2.paymentsystem.global.pagination.PageableFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointService {

    private final PointTransactionRepository pointTransactionRepository;
    private final UserRepository userRepository;

    /**
     * 결제 완료 시 포인트를 적립하고 원장에 기록합니다.
     * PG 실결제 금액의 1% 를 적립합니다.
     */
    @Transactional
    public void earnPoints(Payment payment) {

        Long rewardPointAmount = payment.getRewardPointAmount();

        if (rewardPointAmount == 0L) {return;}

        // 같은 유저의 포인트를 동시에 수정하지 못하도록 users row에 비관락을 겁니다
        User user = findUserForPointUpdate(payment);

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
     * 주문 생성 시 포인트 예약 (잔액 차감) 후 원장에 기록합니다.
     */
    @Transactional
    public void reserveUsedPoints(Payment payment) {

        validatePayment(payment);

        Long amount = payment.getUsedPointAmount();

        // 포인트를 사용하지 않는 주문이라면 예약을 하지 않습니다.
        if (amount == 0L) {return;}

        User user = findUserForPointUpdate(payment);

        if (user.getPointBalance() < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINT);
        }

        // 예약 방식: 실제 잔액에서 선차감하여 다른 주문에서 못 쓰게 막습니다.
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
     * 결제 성공 시 예약을 확정합니다.
     * 유저의 현재 잔액 포인트(user.point_balance) 잔액 차감은 없습니다.
     */
    @Transactional
    public void confirmReservedPoints(Payment payment) {

        validatePayment(payment);
        Long amount = payment.getUsedPointAmount();

        if (amount == 0L) {return;}

        PointTransaction reservedTransaction = pointTransactionRepository
                .findByPayment_IdAndType(payment.getId(), PointTransactionType.USE_RESERVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.POINT_ERROR_EXCEPTION));

        // 예약 원장을 최종 사용 원장으로 변경합니다.
        // 이미 주문 생성 시점에 잔액은 차감했으므로 재차감은 하지 않습니다.
        reservedTransaction.confirmUse();
    }

    /**
     * 환불 시 결제에 사용했던 포인트를 사용자에게 다시 복구 후 원장에 기록합니다.
     */
    @Transactional
    public void restoreUsedPoints(Payment payment, Refund refund, Long restorePointAmount) {

        // 환불 포인트 처리에 필요한 값들이 정상인지 검증합니다.
        validateRefundPointRequest(payment, refund, restorePointAmount);

        // 복구할 포인트가 없으면 잔액 변경과 거래 내역을 만들지 않고 종료합니다.
        if (restorePointAmount == 0L) {return;}

        User user = findUserForPointUpdate(payment);

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
     * 환불 시 결제 완료 때 적립해줬던 포인트를 회수 후 원장에 기록합니다.
     * 단, 유저의 현재 포인트 잔액이 회수해야하는 포인트보다 부족하다면 포인트 잔액을 음수로 만들지 않고 실제 회수 가능한 만큼 회수합니다.
     * 부족한 포인트 금액은 화불 금액에서 차감해야하므로 반환해줍니다.
     */
    @Transactional
    public EarnCancelResult cancelEarnedPoints(Payment payment, Refund refund, Long cancelPointAmount) {

        validateRefundPointRequest(payment, refund, cancelPointAmount);

        if (cancelPointAmount == 0L) {
            return new EarnCancelResult(0L, 0L);
        }

        User user = findUserForPointUpdate(payment);

        long currentUserBalance = user.getPointBalance();

        // 실제로 회수할 수 있는 포인트는 현재 유저의 포인트 잔액과 회수 해야할 포인트 중 더 작은 값입니다.
        long actualCancelAmount = Math.min(currentUserBalance, cancelPointAmount);

        // 부족한 포인트 금액은 실제 PG 환불 금액에서 감액됩니다.
        long shortageAmount = cancelPointAmount - actualCancelAmount;

        // 회수한 포인트를 유저의 현재 포인트 잔액에서 감소시킵니다.
        if (actualCancelAmount > 0) {
            user.decreasePointBalance(actualCancelAmount);

            PointTransaction pointTransaction = PointTransaction.createForRefund(
                    user,
                    payment,
                    refund,
                    PointTransactionType.EARN_CANCEL,
                    actualCancelAmount
            );

            pointTransactionRepository.save(pointTransaction);
        }

        return new EarnCancelResult(actualCancelAmount, shortageAmount);
    }

    /**
     * 주문취소 & 결제 실패 시, 포인트 예약이 취소되며 원장에 기록됩니다.
     * 예약 차감되었던 포인트가 복구 됩니다.
     */
    @Transactional
    public void cancelReservedPoints(Payment payment) {

        validatePayment(payment);

        Long amount = payment.getUsedPointAmount();

        if (amount == 0L) {return;}

        // 포인트 복구 전 중복 점검
        if (pointTransactionRepository.existsByPayment_IdAndType(
                payment.getId(),
                PointTransactionType.USE_CANCEL
        )) {return;}

        User user = findUserForPointUpdate(payment);

        // 결제 PENDING 중 예약하여 차감했던 포인트를 다시 돌려줍니다.
        user.increasePointBalance(amount);


        PointTransaction pointTransaction = PointTransaction.createForPayment(
                user,
                payment,
                PointTransactionType.USE_CANCEL,
                amount
        );

        pointTransactionRepository.save(pointTransaction);
    }

    // 내부 메서드 LIST
    private void validatePayment(Payment payment) {

        if (payment == null || payment.getUsedPointAmount() == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }
    }

    private User findUserForPointUpdate(Payment payment) {
        Long userId = payment.getOrder().getUser().getId();

        return userRepository.findByIdForUpdate(userId).orElseThrow(
                () -> new BusinessException(ErrorCode.USER_NOT_FOUND)
        );
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
     * 적립 포인트 회수 결과입니다.
     * PointService -> RefundService 사이에서 쓰는 내부 결과 Dto 입니다.
     *
     * @param cancelPointAmount: 실제로 회원 잔액에서 회수한 포인트
     * @param shortagePointAmount: 잔액 부족으로 회수하지 못해 환불 금액에서 차감해야 하는 포인트
     */
    public record EarnCancelResult(
            long cancelPointAmount,
            long shortagePointAmount
    ) {}
}
