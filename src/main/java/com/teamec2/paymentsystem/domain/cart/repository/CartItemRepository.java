package com.teamec2.paymentsystem.domain.cart.repository;

import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Query("""
            select ci
            from CartItem ci
            join fetch ci.cart c
            join fetch c.user
            join fetch ci.product
            where ci.id = :id
            """)
    Optional<CartItem> findWithOwnerAndProductById(Long id);

    @Query("""
            select ci
            from CartItem ci
            join fetch ci.product
            where ci.cart.id = :cartId and ci.id = :id
            """)
    Optional<CartItem> findWithProductByCartIdAndId(Long cartId, Long id);

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    @Query("""
            select ci
            from CartItem ci
            join fetch ci.product
            where ci.cart.id = :cartId
            """)
    List<CartItem> findAllWithProductByCartId(Long cartId);

    List<CartItem> findAllByCartId(Long cartId);

    default List<CartItem> findAllInCart(Long cartId) {
        return findAllByCartId(cartId);
    }

    @Query("""
            select coalesce(sum(ci.product.price * ci.quantity), 0)
            from CartItem ci
            where ci.cart.id = :cartId
            """)
    Long sumAmountByCartId(Long cartId);

    long deleteByCartId(Long cartId);

    List<CartItem> findAllByCartIdAndIdIn(Long cartId, List<Long> cartItemIds);

    @Query("""
        select ci
        from CartItem ci
        join fetch ci.product
        where ci.cart.id = :cartId
          and ci.id in :cartItemIds
        """)
    List<CartItem> findAllWithProductByCartIdAndIdIn(Long cartId, List<Long> cartItemIds);

    @Modifying(flushAutomatically = true)
    @Query("""
        delete from CartItem ci
        where ci.cart.id = :cartId
          and ci.id in :cartItemIds
        """)
    int deleteByCartIdAndIdIn(Long cartId, List<Long> cartItemIds);
}
