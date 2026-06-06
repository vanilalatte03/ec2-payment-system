package com.teamec2.paymentsystem.domain.cart.repository;

import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    // 수정/삭제 요청에서 이 장바구니 상품이 로그인한 회원의 것인지 확인할 때 사용합니다.
    @Query("""
            select ci
            from CartItem ci
            join fetch ci.cart c
            join fetch c.user
            join fetch ci.product
            where ci.id = :id
            """)
    Optional<CartItem> findDetailById(Long id);

    // 소유권 확인 후 실제 수정 대상이 여전히 해당 장바구니 안에 있는지 다시 확인할 때 사용합니다.
    @Query("""
            select ci
            from CartItem ci
            join fetch ci.product
            where ci.cart.id = :cartId and ci.id = :id
            """)
    Optional<CartItem> findInCart(Long cartId, Long id);

    // 같은 상품을 다시 담을 때 새 CartItem을 만들지 않고 기존 수량을 늘리기 위해 사용합니다.
    @Query("""
            select ci
            from CartItem ci
            where ci.cart.id = :cartId
              and ci.product.id = :productId
            """)
    Optional<CartItem> findByProduct(Long cartId, Long productId);


    // 화면 응답처럼 상품명, 가격이 바로 필요할 때 join fetch로 N+1 조회를 줄입니다.
    @Query("""
            select ci
            from CartItem ci
            join fetch ci.product
            where ci.cart.id = :cartId
            """)
    List<CartItem> findAllWithProduct(Long cartId);

    // 특정 장바구니에 들어있는 CartItem 목록만 가져오되, Product는 가져오지 않을 때 사용합니다.
    @Query("""
            select ci
            from CartItem ci
            where ci.cart.id = :cartId
            """)
    List<CartItem> findAllInCart(Long cartId);

    // 특정 장바구니의 총 상품 금액을 계산합니다.
    // 각 항목의 상품 가격 * 수량을 더하고, 항목이 없어서 null이 나오면 0으로 바꿉니다.
    @Query("""
            select coalesce(sum(ci.product.price * ci.quantity), 0)
            from CartItem ci
            where ci.cart.id = :cartId
            """)
    Long sumAmount(Long cartId);

    // 특정 장바구니의 모든 CartItem을 삭제합니다.
    // 삭제된 개수를 응답에 사용하기 위해 삭제 개수를 반환합니다.
    long deleteByCartId(Long cartId);

    // Product는 여기서 join fetch하지 않고, OrderService에서 상품 row를 비관락으로 다시 조회합니다.
    @Query("""
            select ci
            from CartItem ci
            where ci.cart.id = :cartId
              and ci.id in :cartItemIds
            """)
    List<CartItem> findAllInCart(Long cartId, List<Long> cartItemIds);

    // 선택한 장바구니 상품만 주문할 때 주문 대상 CartItem을 Product와 함께 조회합니다.
    // 장바구니에 여러 상품이 있어도 cartItemIds에 들어온 항목만 주문 대상으로 삼습니다.
    @Query("""
        select ci
        from CartItem ci
        join fetch ci.product
        where ci.cart.id = :cartId
          and ci.id in :cartItemIds
        """)
    List<CartItem> findAllWithProduct(Long cartId, List<Long> cartItemIds);

    /**
     * 결제 완료 후 주문에 포함된 장바구니 상품만 한 번에 삭제한다.
     *
     * <p>요청한 장바구니 ID와 상품 ID 목록을 함께 조건으로 사용해 다른 사용자의 장바구니 상품이
     * 삭제되지 않도록 범위를 제한한다. 삭제된 행 수는 응답의 장바구니 정리 여부 판단에 사용한다.
     *
     * @param cartId 삭제 대상 장바구니 ID
     * @param cartItemIds 삭제할 장바구니 상품 ID 목록
     * @return 실제 삭제된 장바구니 상품 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        delete from CartItem ci
        where ci.cart.id = :cartId
          and ci.id in :cartItemIds
        """)
    int deleteAllByCartIdAndIdIn(Long cartId, List<Long> cartItemIds);
}
