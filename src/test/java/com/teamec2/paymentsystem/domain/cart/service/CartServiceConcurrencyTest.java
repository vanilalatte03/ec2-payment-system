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
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class CartServiceConcurrencyTest {

    @Autowired
    CartService cartService;

    @Autowired
    CartItemService cartItemService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    CartRepository cartRepository;

    @MockitoSpyBean
    CartItemRepository cartItemRepository;

    @Autowired
    EntityManager entityManager;

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
            assertThat(cartItemRepository.findAllWithProductByCartId(cart.getId())).isEmpty();
            assertThat(cart.getVersion()).isGreaterThan(beforeVersion);
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    void 선택상품정리와상품담기가_동시에실행되어도_성공한담기요청은남아야한다() throws Exception {
        // given
        User user = 회원_저장();
        Product product = 상품_저장("결제 후 다시 담는 상품", 10000, 10, ProductStatus.ON_SALE);
        CartItem orderedCartItem = 장바구니상품_저장(user, product, 1);
        CountDownLatch deleteStartedLatch = new CountDownLatch(1);
        CountDownLatch addCompletedLatch = new CountDownLatch(1);

        doAnswer(invocation -> {
            deleteStartedLatch.countDown();
            assertThat(addCompletedLatch.await(3, TimeUnit.SECONDS)).isTrue();
            return entityManager.createQuery("""
                            delete from CartItem ci
                            where ci.cart.id = :cartId
                              and ci.id in :cartItemIds
                            """)
                    .setParameter("cartId", invocation.getArgument(0))
                    .setParameter("cartItemIds", invocation.getArgument(1))
                    .executeUpdate();
        }).when(cartItemRepository).deleteByCartIdAndIdIn(anyLong(), anyList());

        ExecutorService executorService = Executors.newFixedThreadPool(2);

        try {
            Future<ClearCartResponse> clearFuture = executorService.submit(() ->
                    cartService.clearItems(user.getId(), List.of(orderedCartItem.getId()))
            );
            Future<Void> addFuture = executorService.submit(() -> {
                assertThat(deleteStartedLatch.await(3, TimeUnit.SECONDS)).isTrue();
                try {
                    cartItemService.addItem(user.getId(), product.getId(), 1);
                } finally {
                    addCompletedLatch.countDown();
                }
                return null;
            });

            addFuture.get();
            assertOptimisticLockFailed(clearFuture);
        } finally {
            executorService.shutdown();
        }

        // then
        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow();
        List<CartItem> cartItems = cartItemRepository.findAllWithProductByCartId(cart.getId());

        assertThat(cartItems).hasSize(1);
        assertThat(cartItems.get(0).getProduct().getId()).isEqualTo(product.getId());
        assertThat(cartItems.get(0).getQuantity()).isEqualTo(2);
    }

    private void assertOptimisticLockFailed(Future<ClearCartResponse> future) throws Exception {
        try {
            future.get();
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOfAny(
                    ObjectOptimisticLockingFailureException.class,
                    OptimisticLockException.class
            );
            return;
        }

        throw new AssertionError("장바구니 선택 삭제는 동시에 성공한 상품 담기 요청과 충돌해야 합니다.");
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
