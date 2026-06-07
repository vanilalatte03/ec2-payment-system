package com.teamec2.paymentsystem.domain.cart.repository;

import com.teamec2.paymentsystem.domain.cart.entity.Cart;
import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductCategory;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;
import com.teamec2.paymentsystem.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CartItemRepositoryTest {

    @Autowired
    CartItemRepository cartItemRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void 장바구니ID와상품ID로_기존장바구니상품을조회한다() {
        // given
        User user = 회원_저장();
        Cart cart = 장바구니_저장(user);
        Product product = 상품_저장("조회 상품", 12000);
        CartItem cartItem = 장바구니상품_저장(cart, product, 2);

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<CartItem> foundCartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId());
        Optional<CartItem> notFoundCartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), -1L);

        // then
        assertThat(foundCartItem).isPresent();
        assertThat(foundCartItem.get().getId()).isEqualTo(cartItem.getId());
        assertThat(notFoundCartItem).isEmpty();
    }

    @Test
    void 장바구니상품상세조회는_소유자와상품을함께조회한다() {
        // given
        User user = 회원_저장();
        Cart cart = 장바구니_저장(user);
        Product product = 상품_저장("상세 조회 상품", 15000);
        CartItem cartItem = 장바구니상품_저장(cart, product, 3);

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<CartItem> foundCartItem = cartItemRepository.findWithOwnerAndProductById(cartItem.getId());
        Optional<CartItem> notFoundCartItem = cartItemRepository.findWithOwnerAndProductById(-1L);

        // then
        assertThat(foundCartItem).isPresent();
        assertThat(foundCartItem.get().getId()).isEqualTo(cartItem.getId());
        assertThat(entityManager.getEntityManagerFactory()
                .getPersistenceUnitUtil()
                .isLoaded(foundCartItem.get(), "cart")).isTrue();
        assertThat(entityManager.getEntityManagerFactory()
                .getPersistenceUnitUtil()
                .isLoaded(foundCartItem.get().getCart(), "user")).isTrue();
        assertThat(entityManager.getEntityManagerFactory()
                .getPersistenceUnitUtil()
                .isLoaded(foundCartItem.get(), "product")).isTrue();
        assertThat(notFoundCartItem).isEmpty();
    }

    @Test
    void 장바구니상품목록은_상품과함께조회하고_총액을계산한다() {
        // given
        User user = 회원_저장();
        Cart cart = 장바구니_저장(user);
        장바구니상품_저장(cart, 상품_저장("첫 번째 상품", 10000), 2);
        장바구니상품_저장(cart, 상품_저장("두 번째 상품", 30000), 1);

        entityManager.flush();
        entityManager.clear();

        // when
        List<CartItem> cartItems = cartItemRepository.findAllWithProductByCartId(cart.getId());
        Long totalAmount = cartItemRepository.sumAmountByCartId(cart.getId());

        // then
        assertThat(cartItems).hasSize(2);
        assertThat(cartItems)
                .extracting(cartItem -> cartItem.getProduct().getName())
                .containsExactlyInAnyOrder("첫 번째 상품", "두 번째 상품");
        assertThat(cartItems)
                .allSatisfy(cartItem -> assertThat(entityManager.getEntityManagerFactory()
                        .getPersistenceUnitUtil()
                        .isLoaded(cartItem, "product")).isTrue());
        assertThat(totalAmount).isEqualTo(50000L);
    }

    @Test
    void 주문대상조회는_선택한장바구니상품만조회하고_상품은함께가져오지않는다() {
        // given
        User user = 회원_저장();
        Cart cart = 장바구니_저장(user);
        CartItem selectedCartItem = 장바구니상품_저장(cart, 상품_저장("선택 상품", 10000), 1);
        장바구니상품_저장(cart, 상품_저장("제외 상품", 20000), 1);

        entityManager.flush();
        entityManager.clear();

        // when
        List<CartItem> cartItems = cartItemRepository.findAllByCartIdAndIdIn(
                cart.getId(),
                List.of(selectedCartItem.getId())
        );

        // then
        assertThat(cartItems).hasSize(1);
        assertThat(cartItems.get(0).getId()).isEqualTo(selectedCartItem.getId());
        assertThat(entityManager.getEntityManagerFactory()
                .getPersistenceUnitUtil()
                .isLoaded(cartItems.get(0), "product")).isFalse();
    }

    @Test
    void 장바구니ID로_상품을삭제하면_삭제개수를반환한다() {
        // given
        User user = 회원_저장();
        Cart cart = 장바구니_저장(user);
        장바구니상품_저장(cart, 상품_저장("첫 번째 삭제 상품", 10000), 1);
        장바구니상품_저장(cart, 상품_저장("두 번째 삭제 상품", 20000), 1);

        entityManager.flush();
        entityManager.clear();

        // when
        long deletedCount = cartItemRepository.deleteByCartId(cart.getId());

        // then
        assertThat(deletedCount).isEqualTo(2);
        assertThat(cartItemRepository.findAllByCartId(cart.getId())).isEmpty();
    }

    @Test
    void 장바구니ID와상품ID목록으로삭제하면_해당장바구니상품만삭제한다() {
        // given
        User user = 회원_저장();
        User otherUser = 회원_저장();
        Cart cart = 장바구니_저장(user);
        Cart otherCart = 장바구니_저장(otherUser);
        CartItem selectedCartItem = 장바구니상품_저장(cart, 상품_저장("삭제 대상 상품", 10000), 1);
        CartItem remainingCartItem = 장바구니상품_저장(cart, 상품_저장("유지 대상 상품", 20000), 1);
        CartItem otherCartItem = 장바구니상품_저장(otherCart, 상품_저장("다른 회원 상품", 30000), 1);

        entityManager.flush();
        entityManager.clear();

        // when
        int deletedCount = cartItemRepository.deleteByCartIdAndIdIn(
                cart.getId(),
                List.of(selectedCartItem.getId(), otherCartItem.getId())
        );

        // then
        assertThat(deletedCount).isEqualTo(1);
        assertThat(cartItemRepository.findById(selectedCartItem.getId())).isEmpty();
        assertThat(cartItemRepository.findById(remainingCartItem.getId())).isPresent();
        assertThat(cartItemRepository.findById(otherCartItem.getId())).isPresent();
    }

    private User 회원_저장() {
        User user = User.create(uniqueEmail(), "Password123!", "홍길동", "010-1234-5678");
        entityManager.persist(user);
        return user;
    }

    private Cart 장바구니_저장(User user) {
        Cart cart = new Cart(user);
        entityManager.persist(cart);
        return cart;
    }

    private Product 상품_저장(String name, int price) {
        Product product = new Product(
                name,
                price,
                10,
                "테스트 상품입니다.",
                ProductStatus.ON_SALE,
                ProductCategory.TOP
        );
        entityManager.persist(product);
        return product;
    }

    private CartItem 장바구니상품_저장(Cart cart, Product product, int quantity) {
        CartItem cartItem = new CartItem(cart, product, quantity);
        entityManager.persist(cartItem);
        return cartItem;
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }
}
