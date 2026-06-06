package com.teamec2.paymentsystem.domain.product.repository;

import com.teamec2.paymentsystem.domain.product.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * 주문 생성 중 재고를 차감하기 전에 상품 row를 쓰기 잠금으로 조회합니다.
     *
     * <p>비관락을 걸면 같은 상품을 주문하는 다른 트랜잭션은 이 트랜잭션이 끝날 때까지 기다립니다.
     * 그래서 동시에 같은 재고 값을 읽고 각각 차감하는 lost update 문제를 막을 수 있습니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Product p
            where p.id in :productIds
            order by p.id
            """)
    List<Product> findAllByIdInForUpdate(List<Long> productIds);
}
