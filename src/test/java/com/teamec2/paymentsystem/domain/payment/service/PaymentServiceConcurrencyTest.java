package com.teamec2.paymentsystem.domain.payment.service;

import com.teamec2.paymentsystem.domain.cart.entity.Cart;
import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.cart.repository.CartItemRepository;
import com.teamec2.paymentsystem.domain.cart.repository.CartRepository;
import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.order.entity.OrderStatus;
import com.teamec2.paymentsystem.domain.order.repository.OrderItemRepository;
import com.teamec2.paymentsystem.domain.order.repository.OrderRepository;
import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentRequest;
import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentResponse;
import com.teamec2.paymentsystem.domain.payment.dto.PaymentCancelResponse;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentStatus;
import com.teamec2.paymentsystem.domain.payment.facade.PaymentFacade;
import com.teamec2.paymentsystem.domain.payment.port.PaymentCancelStatus;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGateway;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGatewayResponse;
import com.teamec2.paymentsystem.domain.payment.repository.PaymentRepository;
import com.teamec2.paymentsystem.domain.point.entity.PointTransaction;
import com.teamec2.paymentsystem.domain.point.enums.PointTransactionType;
import com.teamec2.paymentsystem.domain.point.repository.PointTransactionRepository;
import com.teamec2.paymentsystem.domain.point.service.PointService;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductCategory;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;
import com.teamec2.paymentsystem.domain.product.repository.ProductRepository;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PaymentServiceConcurrencyTest {

    @Autowired
    PaymentFacade paymentFacade;

    @Autowired
    UserRepository userRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderItemRepository orderItemRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    PointService pointService;

    @Autowired
    PointTransactionRepository pointTransactionRepository;

    @Autowired
    CartItemRepository cartItemRepository;

    @Autowired
    CartRepository cartRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    TestPaymentGateway testPaymentGateway;

    @BeforeEach
    void setUp() {
        clearDatabase();
        testPaymentGateway.reset();
    }

    @AfterEach
    void tearDown() {
        clearDatabase();
        testPaymentGateway.reset();
    }

    private void clearDatabase() {
        pointTransactionRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 결제확정_동시에요청해도_주문결제와포인트원장은한번만완료된다() throws Exception {
        // given
        int requestCount = 5;
        User user = 회원_저장(10000L);
        CartFixture cartFixture = 장바구니상품_저장(user);
        Order order = 주문_저장(user, 1000L, 200L);
        주문상품_저장(order, cartFixture.orderedItem());
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        pointService.reserveUsedPoints(payment);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);
        testPaymentGateway.success(payment.getPortonePaymentId(), 800L, approvedAt);
        testPaymentGateway.waitForConcurrentGatewayCalls(requestCount);

        List<Callable<ConfirmPaymentResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            tasks.add(() -> paymentFacade.confirmPayment(
                    user.getId(),
                    new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
            ));
        }

        // when
        List<ConfirmPaymentResponse> responses = 동시에_실행(tasks);

        // then
        Order foundOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        User foundUser = userRepository.findById(user.getId()).orElseThrow();

        assertThat(responses).hasSize(requestCount);
        assertThat(responses).allSatisfy(response -> {
            assertThat(response.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(response.cartCleared()).isTrue();
        });
        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(foundPayment.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(foundPayment.isCartCleared()).isTrue();
        assertThat(foundUser.getPointBalance()).isEqualTo(9808L);
        assertThat(cartItemRepository.findAllInCart(cartFixture.cart().getId())).isEmpty();
        assertThat(pointTransactionRepository.findAll())
                .extracting(PointTransaction::getType)
                .containsExactlyInAnyOrder(PointTransactionType.USE, PointTransactionType.EARN);
    }

    @Test
    void 결제확정과웹훅확정이_동시에요청되어도_한번만완료된다() throws Exception {
        // given
        User user = 회원_저장(10000L);
        CartFixture cartFixture = 장바구니상품_저장(user);
        Order order = 주문_저장(user, 1000L, 200L);
        주문상품_저장(order, cartFixture.orderedItem());
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        pointService.reserveUsedPoints(payment);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);
        testPaymentGateway.success(payment.getPortonePaymentId(), 800L, approvedAt);
        testPaymentGateway.waitForConcurrentGatewayCalls(2);

        List<Callable<ConfirmPaymentResponse>> tasks = List.of(
                () -> paymentFacade.confirmPayment(
                        user.getId(),
                        new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
                ),
                () -> paymentFacade.confirmPaidWebhook(payment.getPortonePaymentId())
        );

        // when
        List<ConfirmPaymentResponse> responses = 동시에_실행(tasks);

        // then
        Order foundOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        User foundUser = userRepository.findById(user.getId()).orElseThrow();

        assertThat(responses).hasSize(2);
        assertThat(responses).allSatisfy(response -> {
            assertThat(response.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(response.cartCleared()).isTrue();
        });
        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(foundPayment.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(foundPayment.isCartCleared()).isTrue();
        assertThat(foundUser.getPointBalance()).isEqualTo(9808L);
        assertThat(cartItemRepository.findAllInCart(cartFixture.cart().getId())).isEmpty();
        assertThat(pointTransactionRepository.findAll())
                .extracting(PointTransaction::getType)
                .containsExactlyInAnyOrder(PointTransactionType.USE, PointTransactionType.EARN);
    }

    private List<ConfirmPaymentResponse> 동시에_실행(List<Callable<ConfirmPaymentResponse>> tasks) throws Exception {
        int requestCount = tasks.size();
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<ConfirmPaymentResponse>> futures = new ArrayList<>();

        try {
            for (Callable<ConfirmPaymentResponse> task : tasks) {
                futures.add(executorService.submit(동시시작작업(task, readyLatch, startLatch)));
            }

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            List<ConfirmPaymentResponse> responses = new ArrayList<>();
            for (Future<ConfirmPaymentResponse> future : futures) {
                responses.add(future.get());
            }

            return responses;
        } finally {
            executorService.shutdown();
        }
    }

    private Callable<ConfirmPaymentResponse> 동시시작작업(
            Callable<ConfirmPaymentResponse> task,
            CountDownLatch readyLatch,
            CountDownLatch startLatch
    ) {
        return () -> {
            readyLatch.countDown();
            startLatch.await();
            return task.call();
        };
    }

    private User 회원_저장(Long pointBalance) {
        User user = User.create(uniqueEmail(), "Password123!", "홍길동", "010-1234-5678");

        if (pointBalance > 0) {
            user.increasePointBalance(pointBalance);
        }

        return userRepository.save(user);
    }

    private CartFixture 장바구니상품_저장(User user) {
        Product product = productRepository.save(new Product(
                "후드 집업",
                55000,
                10,
                "테스트 상품",
                ProductStatus.ON_SALE,
                ProductCategory.TOP
        ));
        Cart cart = cartRepository.save(new Cart(user));
        CartItem orderedItem = cartItemRepository.save(new CartItem(cart, product, 1));

        return new CartFixture(cart, orderedItem);
    }

    private Order 주문_저장(User user, Long totalAmount, Long usedPointAmount) {
        return orderRepository.save(Order.create(user, uniqueOrderNumber(), totalAmount, usedPointAmount));
    }

    private OrderItem 주문상품_저장(Order order, CartItem cartItem) {
        return orderItemRepository.save(new OrderItem(
                order,
                cartItem.getProduct(),
                cartItem.getId(),
                cartItem.getQuantity()
        ));
    }

    private Payment 결제_저장(Order order, Long totalAmount, Long usedPointAmount, Long pgAmount) {
        return paymentRepository.save(Payment.createPending(order, totalAmount, usedPointAmount, pgAmount, pgAmount / 100));
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    private String uniqueOrderNumber() {
        return "ORDER-" + UUID.randomUUID();
    }

    private record CartFixture(
            Cart cart,
            CartItem orderedItem
    ) {
    }

    @TestConfiguration
    static class PaymentServiceConcurrencyTestConfig {

        @Bean
        @Primary
        TestPaymentGateway testPaymentGateway() {
            return new TestPaymentGateway();
        }
    }

    static class TestPaymentGateway implements PaymentGateway {

        private volatile PaymentGatewayResponse response;
        private volatile CountDownLatch gatewayCallLatch = new CountDownLatch(0);

        @Override
        public PaymentGatewayResponse getPayment(String paymentId) {
            CountDownLatch currentLatch = gatewayCallLatch;
            currentLatch.countDown();

            try {
                currentLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }

            return response;
        }

        @Override
        public PaymentCancelResponse cancelPayment(
                String paymentId,
                Long cancelAmount,
                Long currentCancellableAmount,
                String reason,
                String idempotencyKey
        ) {
            return new PaymentCancelResponse("cancel_test", "SUCCEEDED", PaymentCancelStatus.SUCCEEDED);
        }

        void success(String paymentId, Long paidAmount, LocalDateTime approvedAt) {
            response = new PaymentGatewayResponse(paymentId, "PAID", paidAmount, approvedAt);
        }

        void waitForConcurrentGatewayCalls(int expectedCallCount) {
            gatewayCallLatch = new CountDownLatch(expectedCallCount);
        }

        void reset() {
            response = null;
            gatewayCallLatch = new CountDownLatch(0);
        }
    }
}
