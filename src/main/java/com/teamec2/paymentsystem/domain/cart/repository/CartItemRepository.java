package com.teamec2.paymentsystem.domain.cart.repository;

import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    @Query("""
            select ci
            from CartItem ci
            join fetch ci.product
            where ci.cart.id = :cartId
            """)
    List<CartItem> findAllByCartIdWithProduct(Long cartId);

    @Query("""
            select ci
            from CartItem ci
            join fetch ci.cart c
            join fetch c.user
            join fetch ci.product
            where ci.id = :id
            """)
    Optional<CartItem> findByIdWithCartUserAndProduct(Long id);

    @Query("""
            select ci
            from CartItem ci
            join fetch ci.product
            where ci.cart.id = :cartId and ci.id = :id
            """)
    Optional<CartItem> findByCartIdAndIdWithProduct(Long cartId, Long id);

    List<CartItem> findAllByCartId(Long cartId);

    int countByCartId(Long cartId);

    @Query("""
            select coalesce(sum(ci.product.price * ci.quantity), 0)
            from CartItem ci
            where ci.cart.id = :cartId
            """)
    Long sumLineAmountByCartId(Long cartId);

    void deleteAllByCartId(Long cartId);

    // 장바구니 전체 주문 생성에 사용할 상품 목록입니다.
    // join fetch로 상품까지 함께 가져와서 서비스에서 상품명, 가격, 재고를 바로 사용할 수 있게 합니다.
    @Query("""
        select ci
        from CartItem ci
        join fetch ci.product
        where ci.cart.id = :cartId
        """)
    List<CartItem> findOrderTargetItems(Long cartId);

    // 장바구니 일부 상품만 선택해서 주문 생성할 때 사용하는 조회입니다.
    @Query("""
        select ci
        from CartItem ci
        join fetch ci.product
        where ci.cart.id = :cartId
          and ci.id in :cartItemIds
        """)
    List<CartItem> findOrderTargetItems(Long cartId, List<Long> cartItemIds);
}
