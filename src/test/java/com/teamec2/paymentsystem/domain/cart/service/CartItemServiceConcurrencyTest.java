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
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
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
    void 장바구니상품담기_동시에같은상품을담아도_장바구니상품은하나만남는다() throws Exception {
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

            int successCount = 0;
            for (Future<Void> future : futures) {
                try {
                    future.get();
                    successCount++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOfAny(
                            ObjectOptimisticLockingFailureException.class,
                            OptimisticLockException.class,
                            DataIntegrityViolationException.class
                    );
                }
            }

            assertThat(successCount).isGreaterThan(0);
        } finally {
            executorService.shutdown();
        }

        // then
        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow();
        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId()).orElseThrow();

        assertThat(cartItemRepository.findAllWithProductByCartId(cart.getId())).hasSize(1);
        assertThat(cartItem.getQuantity()).isBetween(1, requestCount);
        assertThat(cart.getVersion()).isGreaterThan(0L);
    }

    @Test
    void 장바구니상품담기_동시에여러상품을담으면_성공한요청만장바구니에반영된다() throws Exception {
        // given
        int requestCount = 10;
        User user = 회원_저장();
        List<Product> products = new ArrayList<>();

        for (int i = 0; i < requestCount; i++) {
            products.add(상품_저장("동시 추가 상품 " + i, 10000 + i, 10, ProductStatus.ON_SALE));
        }

        Long beforeVersion = 장바구니버전(user);
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();

        try {
            for (Product product : products) {
                futures.add(executorService.submit(장바구니상품_동시담기후상품ID반환작업(
                        user.getId(),
                        product.getId(),
                        readyLatch,
                        startLatch
                )));
            }

            readyLatch.await();

            // when
            startLatch.countDown();

            List<Long> succeededProductIds = new ArrayList<>();
            for (Future<Long> future : futures) {
                try {
                    succeededProductIds.add(future.get());
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOfAny(
                            ObjectOptimisticLockingFailureException.class,
                            OptimisticLockException.class
                    );
                }
            }

            // then
            Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow();
            List<CartItem> cartItems = cartItemRepository.findAllWithProductByCartId(cart.getId());
            List<Long> savedProductIds = cartItems.stream()
                    .map(cartItem -> cartItem.getProduct().getId())
                    .toList();

            assertThat(succeededProductIds).isNotEmpty();
            assertThat(cartItems).hasSize(succeededProductIds.size());
            assertThat(savedProductIds).containsExactlyInAnyOrderElementsOf(succeededProductIds);
            assertThat(cart.getVersion()).isGreaterThan(beforeVersion);
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    void 장바구니상품수량변경_동시에요청하면_성공한요청의수량만최종반영된다() throws Exception {
        // given
        int requestCount = 10;
        User user = 회원_저장();
        Product product = 상품_저장("동시 수량 변경 상품", 10000, 20, ProductStatus.ON_SALE);
        CartItem cartItem = 장바구니상품_저장(user, product, 1);

        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                int targetQuantity = i + 2;
                futures.add(executorService.submit(장바구니상품수량_동시변경작업(
                        user.getId(),
                        cartItem.getId(),
                        targetQuantity,
                        readyLatch,
                        startLatch
                )));
            }

            readyLatch.await();

            // when
            startLatch.countDown();

            List<Integer> succeededQuantities = new ArrayList<>();
            for (Future<Integer> future : futures) {
                try {
                    succeededQuantities.add(future.get());
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOfAny(
                            ObjectOptimisticLockingFailureException.class,
                            OptimisticLockException.class
                    );
                }
            }

            // then
            CartItem changedCartItem = cartItemRepository.findById(cartItem.getId()).orElseThrow();

            assertThat(succeededQuantities).isNotEmpty();
            assertThat(changedCartItem.getQuantity()).isIn(succeededQuantities);
        } finally {
            executorService.shutdown();
        }
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
            cartItemService.addItem(userId, productId, 1);
            return null;
        };
    }

    private Callable<Long> 장바구니상품_동시담기후상품ID반환작업(
            Long userId,
            Long productId,
            CountDownLatch readyLatch,
            CountDownLatch startLatch
    ) {
        return () -> {
            readyLatch.countDown();
            startLatch.await();
            cartItemService.addItem(userId, productId, 1);
            return productId;
        };
    }

    private Callable<Integer> 장바구니상품수량_동시변경작업(
            Long userId,
            Long cartItemId,
            int targetQuantity,
            CountDownLatch readyLatch,
            CountDownLatch startLatch
    ) {
        return () -> {
            readyLatch.countDown();
            startLatch.await();
            cartItemService.updateQuantity(userId, cartItemId, targetQuantity);
            return targetQuantity;
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
