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
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CartServiceConcurrencyTest {

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
    void 장바구니전체삭제_동시에요청하면_최종적으로장바구니가비워진다() throws Exception {
        // given
        int requestCount = 10;
        User user = 회원_저장();
        Product firstProduct = 상품_저장("동시 전체 삭제 상품 1", 10000, 10, ProductStatus.ON_SALE);
        Product secondProduct = 상품_저장("동시 전체 삭제 상품 2", 20000, 10, ProductStatus.ON_SALE);
        장바구니상품_저장(user, firstProduct, 1);
        장바구니상품_저장(user, secondProduct, 2);

        Long beforeVersion = 장바구니버전(user);
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<ClearCartResponse>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                futures.add(executorService.submit(장바구니_동시전체삭제작업(user.getId(), readyLatch, startLatch)));
            }

            readyLatch.await();

            // when
            startLatch.countDown();

            int successCount = 0;
            for (Future<ClearCartResponse> future : futures) {
                try {
                    future.get();
                    successCount++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOfAny(
                            ObjectOptimisticLockingFailureException.class,
                            OptimisticLockException.class
                    );
                }
            }

            // then
            Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow();

            assertThat(successCount).isGreaterThan(0);
            assertThat(cartItemRepository.findWithProductByCartId(cart.getId())).isEmpty();
            assertThat(cart.getVersion()).isGreaterThan(beforeVersion);
        } finally {
            executorService.shutdown();
        }
    }

    private Callable<ClearCartResponse> 장바구니_동시전체삭제작업(
            Long userId,
            CountDownLatch readyLatch,
            CountDownLatch startLatch
    ) {
        return () -> {
            readyLatch.countDown();
            startLatch.await();
            return cartService.clearCart(userId);
        };
    }

    private CartItem 장바구니상품_저장(User user, Product product, int quantity) {
        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow();
        return cartItemRepository.save(new CartItem(cart, product, quantity));
    }

    private User 회원_저장() {
        User user = userRepository.save(User.create(uniqueEmail(), "Password123!", "홍길동", "010-1234-5678"));
        cartRepository.save(new Cart(user));
        return user;
    }

    private Product 상품_저장(String name, int price, int stock, ProductStatus status) {
        return productRepository.save(new Product(name, price, stock, "테스트 상품입니다.", status, ProductCategory.TOP));
    }

    private Long 장바구니버전(User user) {
        return cartRepository.findByUserId(user.getId()).orElseThrow().getVersion();
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }
}
