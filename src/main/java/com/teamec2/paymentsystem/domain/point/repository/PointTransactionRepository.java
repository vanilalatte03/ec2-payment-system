package com.teamec2.paymentsystem.domain.point.repository;

import com.teamec2.paymentsystem.domain.point.entity.PointTransaction;
import com.teamec2.paymentsystem.domain.point.enums.PointTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    /**
     * 회원의 포인트 거래 원장을 합산하여 계산된 잔액을 조회합니다.
     */
    @Query("""
        select coalesce(sum(
            case
                when pt.type in :increaseTypes then pt.amount
                when pt.type in :decreaseTypes then (0 - pt.amount)
                else 0
            end
        ), 0)
        from PointTransaction pt
        where pt.user.id = :userId
        """)
    Long calculateLedgerBalance(
            @Param("userId") Long userId,
            @Param("increaseTypes") Collection<PointTransactionType> increaseTypes,
            @Param("decreaseTypes") Collection<PointTransactionType> decreaseTypes
    );

   Page<PointTransaction> findAllByUser_Id(Long userId, Pageable pageable);

   Page<PointTransaction> findAllByUser_IdAndType(
           Long userId,
           PointTransactionType type,
           Pageable pageable
   );

    /**
     * 멱등 키가 이미 존재하는지 확인합니다.
     * 잔액 변경 전에 중복 처리 여부를 빠르게 판단하기 위해 사용합니다.
     */
    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * 멱등 키로 포인트 원장을 조회합니다.
     * 예약 확정이나 중복 호출 처리에서 기존 원장을 찾기 위해 사용합니다.
     */
    Optional<PointTransaction> findByIdempotencyKey(String idempotencyKey);
}
