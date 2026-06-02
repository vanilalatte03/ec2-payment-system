package com.teamec2.paymentsystem.domain.cart.repository;

import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ci
            from CartItem ci
            join fetch ci.product
            where ci.cart.id = :cartId and ci.product.id = :productId
            """)
    Optional<CartItem> findByCartIdAndProductIdForUpdate(Long cartId, Long productId);

    List<CartItem> findAllByCartId(Long cartId);

    int countByCartId(Long cartId);

    @Query("""
            select coalesce(sum(ci.product.price * ci.quantity), 0)
            from CartItem ci
            where ci.cart.id = :cartId
            """)
    Long sumLineAmountByCartId(Long cartId);

    void deleteAllByCartId(Long cartId);
}
