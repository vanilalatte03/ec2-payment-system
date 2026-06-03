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

   Optional<PointTransaction> findByPayment_IdAndType(
           Long paymentId,
           PointTransactionType type
   );

    boolean existsByPayment_IdAndType(Long paymentId, PointTransactionType type);
}
