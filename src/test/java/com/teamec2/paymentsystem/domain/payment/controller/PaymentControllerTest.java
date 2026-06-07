package com.teamec2.paymentsystem.domain.payment.controller;

import com.teamec2.paymentsystem.domain.cart.entity.Cart;
import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.cart.repository.CartItemRepository;
import com.teamec2.paymentsystem.domain.cart.repository.CartRepository;
import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.order.entity.OrderStatus;
import com.teamec2.paymentsystem.domain.order.repository.OrderItemRepository;
import com.teamec2.paymentsystem.domain.order.repository.OrderRepository;
import com.teamec2.paymentsystem.domain.payment.dto.PaymentCancelResponse;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentStatus;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentType;
import com.teamec2.paymentsystem.domain.payment.port.PaymentCancelStatus;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGateway;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGatewayResponse;
import com.teamec2.paymentsystem.domain.payment.repository.PaymentRepository;
import com.teamec2.paymentsystem.domain.point.repository.PointTransactionRepository;
import com.teamec2.paymentsystem.domain.point.service.PointService;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductCategory;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;
import com.teamec2.paymentsystem.domain.product.repository.ProductRepository;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.domain.user.repository.UserRepository;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import com.teamec2.paymentsystem.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTest {

    private static final int BODY_STATUS = 200;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderItemRepository orderItemRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    PointTransactionRepository pointTransactionRepository;

    @Autowired
    PointService pointService;

    @Autowired
    CartItemRepository cartItemRepository;

    @Autowired
    CartRepository cartRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

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
    void 결제확정_성공하면_200과응답데이터를반환한다() throws Exception {
        // given
        User user = 회원_저장(200L);
        CartFixture cartFixture = 장바구니상품_저장(user);
        Order order = 주문_저장(user, 1000L, 200L);
        주문상품_저장(order, cartFixture.orderedItem());
        Payment payment = 포인트_예약된_결제_저장(order, 1000L, 200L, 800L);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);
        testPaymentGateway.success(payment.getPortonePaymentId(), 800L, approvedAt);

        // when
        // then
        mockMvc.perform(post("/api/payments/confirm")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": %d,
                                  "portonePaymentId": "%s"
                                }
                                """.formatted(order.getId(), payment.getPortonePaymentId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.message").value("요청이 성공했습니다."))
                .andExpect(jsonPath("$.data.orderId").value(order.getId()))
                .andExpect(jsonPath("$.data.orderNumber").value(order.getOrderNumber()))
                .andExpect(jsonPath("$.data.orderStatus").value(OrderStatus.COMPLETED.name()))
                .andExpect(jsonPath("$.data.paymentId").value(payment.getId()))
                .andExpect(jsonPath("$.data.portonePaymentId").value(payment.getPortonePaymentId()))
                .andExpect(jsonPath("$.data.paymentStatus").value(PaymentStatus.COMPLETED.name()))
                .andExpect(jsonPath("$.data.paymentType").value(PaymentType.POINT_CARD.name()))
                .andExpect(jsonPath("$.data.totalAmount").value(1000))
                .andExpect(jsonPath("$.data.usedPointAmount").value(200))
                .andExpect(jsonPath("$.data.pgAmount").value(800))
                .andExpect(jsonPath("$.data.rewardPointAmount").value(8))
                .andExpect(jsonPath("$.data.cartCleared").value(true))
                .andExpect(jsonPath("$.data.approvedAt").value("2026-06-01T12:30:00+09:00"));

        Order foundOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        User foundUser = userRepository.findById(user.getId()).orElseThrow();

        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(foundPayment.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(foundUser.getPointBalance()).isEqualTo(8L);
        assertThat(pointTransactionRepository.count()).isEqualTo(2);
        assertThat(testPaymentGateway.getCallCount()).isEqualTo(1);
        assertThat(cartItemRepository.findAllInCart(cartFixture.cart().getId())).isEmpty();
    }

    @Test
    void 결제확정_토큰이없으면_UNAUTHORIZED를반환한다() throws Exception {
        // when
        // then
        mockMvc.perform(post("/api/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": 1,
                                  "portonePaymentId": "pay_123"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 결제확정_요청값이없으면_VALIDATION_FAILED를반환한다() throws Exception {
        // given
        User user = 회원_저장();

        // when
        // then
        mockMvc.perform(post("/api/payments/confirm")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "portonePaymentId": ""
                                }
                                """))
                .andExpect(status().is(ErrorCode.VALIDATION_FAILED.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.data").isArray());

        assertThat(testPaymentGateway.getCallCount()).isZero();
    }

    private User 회원_저장() {
        return 회원_저장(0L);
    }

    private User 회원_저장(Long pointBalance) {
        User user = User.create(uniqueEmail(), "Password123!", "홍길동", "010-1234-5678");

        // 포인트를 사용하는 결제 fixture는 실제 주문 생성 정책처럼 충분한 잔액을 미리 준비합니다.
        if (pointBalance > 0) {
            user.increasePointBalance(pointBalance);
        }

        return userRepository.save(user);
    }

    private Order 주문_저장(User user, Long totalAmount, Long usedPointAmount) {
        return orderRepository.save(Order.create(user, uniqueOrderNumber(), totalAmount, usedPointAmount));
    }

    private Payment 결제_저장(Order order, Long totalAmount, Long usedPointAmount, Long pgAmount) {
        return paymentRepository.save(Payment.createPending(order, totalAmount, usedPointAmount, pgAmount, pgAmount / 100));
    }

    private Payment 포인트_예약된_결제_저장(Order order, Long totalAmount, Long usedPointAmount, Long pgAmount) {
        Payment payment = 결제_저장(order, totalAmount, usedPointAmount, pgAmount);
        pointService.reserveUsedPoints(payment);
        return payment;
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

    private OrderItem 주문상품_저장(Order order, CartItem cartItem) {
        return orderItemRepository.save(new OrderItem(
                order,
                cartItem.getProduct(),
                cartItem.getId(),
                cartItem.getQuantity()
        ));
    }

    private String accessToken(User user) {
        return jwtTokenProvider.createAccessToken(user.getId());
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
    static class PaymentControllerTestConfig {

        @Bean
        @Primary
        TestPaymentGateway testPaymentGateway() {
            return new TestPaymentGateway();
        }
    }

    static class TestPaymentGateway implements PaymentGateway {

        private PaymentGatewayResponse response;
        private int callCount;

        @Override
        public PaymentGatewayResponse getPayment(String paymentId) {
            callCount++;
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

        int getCallCount() {
            return callCount;
        }

        void reset() {
            response = null;
            callCount = 0;
        }
    }
}
