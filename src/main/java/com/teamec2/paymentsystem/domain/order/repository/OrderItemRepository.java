package com.teamec2.paymentsystem.domain.order.repository;

import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * 주문에 포함된 주문 상품과 연결 상품을 함께 조회한다.
     *
     * <p>결제 보상 취소 후 재고를 복구할 때 주문 상품의 수량과 상품 엔티티가 모두 필요하다.
     * {@code join fetch oi.product}를 사용해 반복문 안에서 상품을 늦게 조회하는 N+1 문제를 피한다.
     *
     * @param orderId 주문 ID
     * @return 상품이 함께 로딩된 주문 상품 목록
     */
    @Query("""
            select oi
            from OrderItem oi
            join fetch oi.product
            where oi.order.id = :orderId
            """)
    List<OrderItem> findAllWithProductByOrderId(Long orderId);
}
