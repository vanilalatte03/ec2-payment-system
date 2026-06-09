package com.teamec2.paymentsystem.domain.refund.repository;

import com.teamec2.paymentsystem.domain.refund.entity.RefundItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RefundItemRepository extends JpaRepository<RefundItem, Long> {

    List<RefundItem> findAllByRefund_Id(Long refundId);

    @Query("""
            select ri
            from RefundItem ri
            join fetch ri.orderItem oi
            where ri.refund.id = :refundId
            """)
    List<RefundItem> findAllByRefundIdWithOrderItem(Long refundId);
}
