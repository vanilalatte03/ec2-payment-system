package com.teamec2.paymentsystem.domain.cart.service;

import com.teamec2.paymentsystem.domain.cart.dto.ClearCartResponse;
import com.teamec2.paymentsystem.domain.cart.entity.Cart;
import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.cart.repository.CartItemRepository;
import com.teamec2.paymentsystem.domain.cart.repository.CartRepository;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductCategory;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;
import com.teamec2.paymentsystem.domain.product.repository.ProductRepository;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CartServiceTest {

    @Autowired
    CartService cartService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    CartRepository cartRepository;

    @Autowired
    CartItemRepository cartItemRepository;

    @BeforeEach
    void setUp() {
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 구매완료상품정리_선택한장바구니상품만삭제하고_나머지는유지한다() {
        // given
        User user = 회원_저장();
        Product firstProduct = 상품_저장("첫 번째 상품");
        Product secondProduct = 상품_저장("두 번째 상품");
        Product thirdProduct = 상품_저장("세 번째 상품");
        Product fourthProduct = 상품_저장("네 번째 상품");
        Product fifthProduct = 상품_저장("다섯 번째 상품");

        CartItem firstCartItem = 장바구니상품_저장(user, firstProduct);
        CartItem secondCartItem = 장바구니상품_저장(user, secondProduct);
        CartItem thirdCartItem = 장바구니상품_저장(user, thirdProduct);
        CartItem fourthCartItem = 장바구니상품_저장(user, fourthProduct);
        CartItem fifthCartItem = 장바구니상품_저장(user, fifthProduct);

        // when
        ClearCartResponse response = cartService.clearPurchasedItems(
                user.getId(),
                List.of(firstCartItem, thirdCartItem)
        );

        // then
        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow();
        List<Long> remainingCartItemIds = cartItemRepository.findAllWithProduct(cart.getId()).stream()
                .map(CartItem::getId)
                .toList();

        assertThat(response.deletedCount()).isEqualTo(2);
        assertThat(remainingCartItemIds)
                .containsExactlyInAnyOrder(
                        secondCartItem.getId(),
                        fourthCartItem.getId(),
                        fifthCartItem.getId()
                );
        assertThat(cartItemRepository.findById(firstCartItem.getId())).isEmpty();
        assertThat(cartItemRepository.findById(thirdCartItem.getId())).isEmpty();
    }

    private User 회원_저장() {
        User user = userRepository.save(User.create(uniqueEmail(), "Password123!", "홍길동", "010-1234-5678"));
        cartRepository.save(new Cart(user));
        return user;
    }

    private Product 상품_저장(String name) {
        return productRepository.save(new Product(
                name,
                10000,
                10,
                "테스트 상품입니다.",
                ProductStatus.ON_SALE,
                ProductCategory.TOP
        ));
    }

    private CartItem 장바구니상품_저장(User user, Product product) {
        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow();
        return cartItemRepository.save(new CartItem(cart, product, 1));
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }
}
