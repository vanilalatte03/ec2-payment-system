package com.teamec2.paymentsystem.domain.refund.repository;

import com.teamec2.paymentsystem.domain.refund.entity.RefundOutbox;
import com.teamec2.paymentsystem.domain.refund.enums.RefundOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefundOutboxRepository extends JpaRepository<RefundOutbox, Long> {

    /**
     * PENDING 상태인 작업의 처리 시간이 된 Outbox ID 목록을 조회합니다.
     * PROCESSING 고착 작업은 findStaleProcessingIds()에서 별도로 조회합니다.
     */
    @Query("""
            select ro.id
            from RefundOutbox ro
            where ro.status = :status
              and ro.nextAttemptAt <= :now
            order by ro.id
            """)
    List<Long> findDueIds(
            @Param("status") RefundOutboxStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    /**
     * 스케줄러에서 호출하기 편하도록 PENDING 상태를 기본값으로 감싼 메서드입니다.
     */
    default List<Long> findDuePendingIds(LocalDateTime now, Pageable pageable) {
        return findDueIds(
                RefundOutboxStatus.PENDING,
                now,
                pageable
        );
    }

    /**
     * 동시에 여러 스케줄러가 같은 Outbox를 처리하는 것을 막기 위해 DB 레벨에서 row lock을 잡습니다.
     * 같은 환불이 PG사에 중복 취소될 위험을 방어합니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ro
            from RefundOutbox ro
            join fetch ro.refund r
            join fetch r.payment p
            join fetch r.order o
            where ro.id = :id
            """)
    Optional<RefundOutbox> findByIdForUpdate(@Param("id") Long id);

    /**
     * PROCESSING 고착 작업 상태를 처리
     *
     * PROCESSING 상태로 오래 남아 있는 Outbox ID 목록을 조회합니다.
     * 서버가 PROCESSING으로 변경한 뒤 PG 호출 직전/직후에 죽으면 해당 작업이 영구히 PROCESSING에 갇힐 수 있기 때문에 필요한 작업입니다.
     */
    @Query("""
            select ro.id
            from RefundOutbox ro
            where ro.status = :status
              and ro.processingStartedAt <= :threshold
            order by ro.id
            """)
    List<Long> findStaleProcessingIds(
            @Param("status") RefundOutboxStatus status,
            @Param("threshold") LocalDateTime threshold,
            Pageable pageable
    );

    /**
     * 스케줄러에서 호출하기 편하도록 PROCESSING 상태를 기본값으로 감싼 메서드입니다.
     */
    default List<Long> findStaleProcessingIds(LocalDateTime threshold, Pageable pageable) {
        return findStaleProcessingIds(
                RefundOutboxStatus.PROCESSING,
                threshold,
                pageable
        );
    }
}