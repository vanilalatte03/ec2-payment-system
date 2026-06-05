package com.teamec2.paymentsystem.domain.cart.repository;

import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    // 특정 장바구니에 특정 상품이 이미 담겨 있는지 확인합니다.
    // 같은 상품을 다시 담을 때 새 CartItem을 만들지 않고 기존 수량을 늘리기 위해 사용합니다.
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    // 특정 장바구니의 상품 목록을 Product와 함께 조회합니다.
    // 화면 응답처럼 상품명, 가격이 바로 필요할 때 join fetch로 N+1 조회를 줄입니다.
    @Query("""
            select ci
            from CartItem ci
            join fetch ci.product
            where ci.cart.id = :cartId
            """)
    List<CartItem> findWithProductByCartId(Long cartId);

    // CartItem 1건을 조회하면서 소유자 User와 상품 Product까지 함께 가져옵니다.
    // 수정/삭제 요청에서 이 장바구니 상품이 로그인한 회원의 것인지 확인할 때 사용합니다.
    @Query("""
            select ci
            from CartItem ci
            join fetch ci.cart c
            join fetch c.user
            join fetch ci.product
            where ci.id = :id
            """)
    Optional<CartItem> findWithOwnerAndProductById(Long id);

    // 특정 장바구니 안에 있는 특정 CartItem을 Product와 함께 조회합니다.
    // 소유권 확인 후 실제 수정 대상이 여전히 해당 장바구니 안에 있는지 다시 확인할 때 사용합니다.
    @Query("""
            select ci
            from CartItem ci
            join fetch ci.product
            where ci.cart.id = :cartId and ci.id = :id
            """)
    Optional<CartItem> findWithProductByCartIdAndId(Long cartId, Long id);

    List<CartItem> findAllByCartId(Long cartId);

    // 특정 장바구니에 담긴 CartItem 개수를 셉니다.
    // 장바구니 비우기 응답에서 삭제 대상 개수를 계산할 때 사용할 수 있습니다.
    int countByCartId(Long cartId);

    // 특정 장바구니의 총 상품 금액을 계산합니다.
    // 각 항목의 상품 가격 * 수량을 더하고, 항목이 없어서 null이 나오면 0으로 바꿉니다.
    @Query("""
            select coalesce(sum(ci.product.price * ci.quantity), 0)
            from CartItem ci
            where ci.cart.id = :cartId
            """)
    Long sumLineAmountByCartId(Long cartId);

    // 특정 장바구니의 모든 CartItem을 삭제합니다.
    // 장바구니 전체 비우기 기능에서 사용합니다.
    void deleteAllByCartId(Long cartId);

    // 선택한 장바구니 상품만 주문할 때 주문 대상 CartItem을 Product와 함께 조회합니다.
    // 장바구니에 여러 상품이 있어도 cartItemIds에 들어온 항목만 주문 대상으로 삼습니다.
    @Query("""
        select ci
        from CartItem ci
        join fetch ci.product
        where ci.cart.id = :cartId
          and ci.id in :cartItemIds
        """)
    List<CartItem> findOrderItemsByCartIdAndIdIn(Long cartId, List<Long> cartItemIds);
}
