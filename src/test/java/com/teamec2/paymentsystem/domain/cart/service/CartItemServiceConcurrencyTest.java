package com.teamec2.paymentsystem.domain.cart.service;

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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CartItemServiceConcurrencyTest {

    @Autowired
    CartItemService cartItemService;

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
    void 장바구니상품담기_동시에같은상품을담아도_하나의항목으로수량이정확히합산된다() throws Exception {
        // given
        int requestCount = 10;
        User user = 회원_저장();
        Product product = 상품_저장("동시성 테스트 상품", 10000, requestCount, ProductStatus.ON_SALE);

        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                futures.add(executorService.submit(장바구니상품_동시담기작업(user.getId(), product.getId(), readyLatch, startLatch)));
            }

            readyLatch.await();

            // when
            startLatch.countDown();

            for (Future<Void> future : futures) {
                future.get();
            }
        } finally {
            executorService.shutdown();
        }

        // then
        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow();
        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId()).orElseThrow();

        assertThat(cartItemRepository.findAllByCartId(cart.getId())).hasSize(1);
        assertThat(cartItem.getQuantity()).isEqualTo(requestCount);
    }

    private Callable<Void> 장바구니상품_동시담기작업(
            Long userId,
            Long productId,
            CountDownLatch readyLatch,
            CountDownLatch startLatch
    ) {
        return () -> {
            readyLatch.countDown();
            startLatch.await();
            cartItemService.addCartItem(userId, productId, 1);
            return null;
        };
    }

    private User 회원_저장() {
        return userRepository.save(User.create(uniqueEmail(), "Password123!", "홍길동", "010-1234-5678"));
    }

    private Product 상품_저장(String name, int price, int stock, ProductStatus status) {
        return productRepository.save(new Product(name, price, stock, "테스트 상품입니다.", status, ProductCategory.TOP));
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }
}
