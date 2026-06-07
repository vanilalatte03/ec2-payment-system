package com.teamec2.paymentsystem.domain.point.service;

import com.teamec2.paymentsystem.domain.point.dto.PointBalanceResponse;
import com.teamec2.paymentsystem.domain.point.dto.PointTransactionResponse;
import com.teamec2.paymentsystem.domain.point.entity.PointTransaction;
import com.teamec2.paymentsystem.domain.point.enums.PointTransactionType;
import com.teamec2.paymentsystem.domain.point.repository.PointTransactionRepository;
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

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointQueryService {

    private final UserRepository userRepository;
    private final PointTransactionRepository pointTransactionRepository;

    /**
     * users.point_balance 스냅샷과 원장 합계를 비교한 후 잔액을 반환합니다.
     */
    public PointBalanceResponse getPointBalance(Long userId) {

        User user = findPointAccount(userId);

        Long ledgerBalance = pointTransactionRepository.calculateLedgerBalance(
                        userId,
                        INCREASE_TYPES,
                        DECREASE_TYPES
                );

        if (!ledgerBalance.equals(user.getPointBalance())) {
            throw new BusinessException(ErrorCode.POINT_LEDGER_SYNC_FAILED);
        }

        return PointBalanceResponse.from(user);
    }

    /**
     * 현재 로그인 사용자의 포인트 거래 내역을 최신순으로 조회합니다.
     *  type == null, 전체 내역 조회
     *  type != null, 해당 거래 유형 조회
     */
    public PageResponse<PointTransactionResponse> getPointTransaction(
            Long userId,
            PointTransactionType type,
            int page,
            int size) {

        findPointAccount(userId);

        // PointTransaction 엔티티의 거래 ID를 보조 정렬 기준으로 사용합니다.
        Sort latestSort = Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "id"));

        Pageable pageable = PageableFactory.create(page, size,latestSort);

        Page<PointTransaction> pointTransactionListPage;

        if (type == null) {
            pointTransactionListPage = pointTransactionRepository.findAllByUser_Id(userId, pageable);
        } else {
            pointTransactionListPage = pointTransactionRepository.findAllByUser_IdAndType(userId, type, pageable);
        }

        Page<PointTransactionResponse> pointTransactionResponsePage = pointTransactionListPage.map(PointTransactionResponse::from);

        return PageResponse.from(pointTransactionResponsePage);
    }

    /**
     * 잔액을 증가시키는 원장 타입입니다.
     */
    private static final Set<PointTransactionType> INCREASE_TYPES =
            Set.of(
                    PointTransactionType.EARN,
                    PointTransactionType.USE_RESTORE,
                    PointTransactionType.USE_CANCEL,
                    PointTransactionType.EARN_RECOVERY_RELEASE
            );

    /**
     * 잔액을 감소시키는 원장 타입입니다.
     */
    private static final Set<PointTransactionType> DECREASE_TYPES =
            Set.of(
                    PointTransactionType.USE,
                    PointTransactionType.EARN_CANCEL,
                    PointTransactionType.USE_RESERVE,
                    PointTransactionType.EARN_RECOVERY_RESERVE
            );

    private User findPointAccount(Long userId) {

        return userRepository.findById(userId).orElseThrow(
                () -> new BusinessException(ErrorCode.POINT_ACCOUNT_NOT_FOUND));
    }
}