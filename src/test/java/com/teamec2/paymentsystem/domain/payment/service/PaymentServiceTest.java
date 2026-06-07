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
import com.teamec2.paymentsystem.domain.payment.entity.PaymentType;
import com.teamec2.paymentsystem.domain.payment.facade.PaymentFacade;
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
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PaymentServiceTest {

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
    void 결제확정_PG결제성공이면_주문과결제를완료한다() {
        // given
        User user = 회원_저장(10000L);
        CartFixture cartFixture = 장바구니상품_저장(user);
        Order order = 주문_저장(user, 1000L, 200L);
        주문상품_저장(order, cartFixture.orderedItem());
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        pointService.reserveUsedPoints(payment);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);
        testPaymentGateway.success(payment.getPortonePaymentId(), 800L, approvedAt);

        // when
        ConfirmPaymentResponse response = paymentFacade.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        );

        // then
        Order foundOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(foundPayment.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(testPaymentGateway.getCallCount()).isEqualTo(1);

        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.paymentId()).isEqualTo(payment.getId());
        assertThat(response.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.paymentType()).isEqualTo(PaymentType.POINT_CARD);
        assertThat(response.pgAmount()).isEqualTo(800L);
        assertThat(response.cartCleared()).isTrue();
        assertThat(response.approvedAt()).isEqualTo(approvedAt.atOffset(ZoneOffset.ofHours(9)));
        assertThat(cartItemRepository.findAllInCart(cartFixture.cart().getId())).isEmpty();
    }

    @Test
    void 결제확정_포인트전액결제이면_PortOne조회없이_완료한다() {
        // given
        User user = 회원_저장(1000L);
        CartFixture cartFixture = 장바구니상품_저장(user);
        Order order = 주문_저장(user, 1000L, 1000L);
        주문상품_저장(order, cartFixture.orderedItem());
        Payment payment = 결제_저장(order, 1000L, 1000L, 0L);
        pointService.reserveUsedPoints(payment);

        // when
        ConfirmPaymentResponse response = paymentFacade.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        );

        // then
        Order foundOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(testPaymentGateway.getCallCount()).isZero();
        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(foundPayment.getApprovedAt()).isNotNull();
        assertThat(response.paymentType()).isEqualTo(PaymentType.POINT_ONLY);
        assertThat(response.pgAmount()).isZero();
        assertThat(response.cartCleared()).isTrue();
        assertThat(cartItemRepository.findAllInCart(cartFixture.cart().getId())).isEmpty();
    }

    @Test
    void 결제확정_이미완료된결제이면_상태변경없이_성공응답한다() {
        // given
        User user = 회원_저장();
        Order order = 주문_저장(user, 1000L, 200L);
        order.complete();
        orderRepository.saveAndFlush(order);

        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);
        payment.complete(approvedAt);
        paymentRepository.saveAndFlush(payment);

        // when
        ConfirmPaymentResponse response = paymentFacade.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        );

        // then
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(testPaymentGateway.getCallCount()).isZero();
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(foundPayment.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(response.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.approvedAt()).isEqualTo(approvedAt.atOffset(ZoneOffset.ofHours(9)));
    }

    @Test
    void 웹훅결제확정_PG결제성공이면_주문결제완료하고_장바구니를정리한다() {
        // given
        User user = 회원_저장(10000L);
        CartFixture cartFixture = 장바구니상품_저장(user);
        Order order = 주문_저장(user, 1000L, 200L);
        주문상품_저장(order, cartFixture.orderedItem());
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        pointService.reserveUsedPoints(payment);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);
        testPaymentGateway.success(payment.getPortonePaymentId(), 800L, approvedAt);

        // when
        ConfirmPaymentResponse response = paymentFacade.confirmPaidWebhook(payment.getPortonePaymentId());

        // then
        Order foundOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(foundPayment.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(testPaymentGateway.getCallCount()).isEqualTo(1);

        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.paymentId()).isEqualTo(payment.getId());
        assertThat(response.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.paymentType()).isEqualTo(PaymentType.POINT_CARD);
        assertThat(response.pgAmount()).isEqualTo(800L);
        assertThat(response.cartCleared()).isTrue();
        assertThat(response.approvedAt()).isEqualTo(approvedAt.atOffset(ZoneOffset.ofHours(9)));
        assertThat(cartItemRepository.findAllInCart(cartFixture.cart().getId())).isEmpty();
    }

    @Test
    void 웹훅결제확정_이미완료된결제이면_PortOne조회없이_멱등응답한다() {
        // given
        User user = 회원_저장();
        Order order = 주문_저장(user, 1000L, 200L);
        order.complete();
        orderRepository.saveAndFlush(order);

        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);
        payment.complete(approvedAt);
        payment.recordCartCleared(true);
        paymentRepository.saveAndFlush(payment);

        // when
        ConfirmPaymentResponse response = paymentFacade.confirmPaidWebhook(payment.getPortonePaymentId());

        // then
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(testPaymentGateway.getCallCount()).isZero();
        assertThat(testPaymentGateway.getCancelCallCount()).isZero();
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(foundPayment.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(response.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.cartCleared()).isTrue();
        assertThat(response.approvedAt()).isEqualTo(approvedAt.atOffset(ZoneOffset.ofHours(9)));
    }

    @Test
    void 웹훅결제확정_PortOne금액이다르면_보상취소후_PAYMENT_AMOUNT_MISMATCH가발생한다() {
        // given
        User user = 회원_저장(1000L);
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        pointService.reserveUsedPoints(payment);
        testPaymentGateway.success(payment.getPortonePaymentId(), 700L, LocalDateTime.of(2026, 6, 1, 12, 30));

        // when
        // then
        assertThatThrownBy(() -> paymentFacade.confirmPaidWebhook(payment.getPortonePaymentId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        assertThat(testPaymentGateway.getCallCount()).isEqualTo(1);
        assertThat(testPaymentGateway.getCancelCallCount()).isEqualTo(1);
        assertThat(testPaymentGateway.getCancelPaymentId()).isEqualTo(payment.getPortonePaymentId());
        assertThat(testPaymentGateway.getCancelAmount()).isEqualTo(700L);
        assertThat(testPaymentGateway.getCancelReason()).isEqualTo("PAYMENT_CONFIRM_INTERNAL_FAILURE");
        assertThat(testPaymentGateway.getCancelIdempotencyKey())
                .isEqualTo("payment-confirm-compensation-" + payment.getId());

        Order foundOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(foundPayment.getFailedAt()).isNotNull();
    }

    @Test
    void 결제확정_타인주문이면_ORDER_ACCESS_DENIED가발생한다() {
        // given
        User owner = 회원_저장();
        User otherUser = 회원_저장();
        Order order = 주문_저장(owner, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);

        // when
        // then
        assertThatThrownBy(() -> paymentFacade.confirmPayment(
                otherUser.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_ACCESS_DENIED);

        assertThat(testPaymentGateway.getCallCount()).isZero();
    }

    @Test
    void 결제확정_PortOne결제ID가다르면_PAYMENT_PORTONE_ID_MISMATCH가발생한다() {
        // given
        User user = 회원_저장();
        Order order = 주문_저장(user, 1000L, 200L);
        결제_저장(order, 1000L, 200L, 800L);

        // when
        // then
        assertThatThrownBy(() -> paymentFacade.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), "pay_mismatch")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_PORTONE_ID_MISMATCH);

        assertThat(testPaymentGateway.getCallCount()).isZero();
    }

    @Test
    void 결제확정_PortOne상태가PAID가아니면_PAYMENT_STATUS_NOT_PAID가발생한다() {
        // given
        User user = 회원_저장();
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        testPaymentGateway.response(
                payment.getPortonePaymentId(),
                "FAILED",
                800L,
                LocalDateTime.of(2026, 6, 1, 12, 30)
        );

        // when
        // then
        assertThatThrownBy(() -> paymentFacade.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_STATUS_NOT_PAID);

        assertThat(testPaymentGateway.getCallCount()).isEqualTo(1);
    }

    @Test
    void 결제확정_PortOne응답이없으면_EXTERNAL_API_FAILED가발생하고_보상취소하지않는다() {
        // given
        User user = 회원_저장();
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);

        // when
        // then
        assertThatThrownBy(() -> paymentFacade.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_API_FAILED);

        Order foundOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(testPaymentGateway.getCallCount()).isEqualTo(1);
        assertThat(testPaymentGateway.getCancelCallCount()).isZero();
        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void 결제확정_PortOne승인금액이없으면_EXTERNAL_API_FAILED가발생하고_보상취소하지않는다() {
        // given
        User user = 회원_저장();
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        testPaymentGateway.success(payment.getPortonePaymentId(), null, LocalDateTime.of(2026, 6, 1, 12, 30));

        // when
        // then
        assertThatThrownBy(() -> paymentFacade.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_API_FAILED);

        Order foundOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(testPaymentGateway.getCallCount()).isEqualTo(1);
        assertThat(testPaymentGateway.getCancelCallCount()).isZero();
        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void 결제확정_PortOne금액이다르면_보상취소후_PAYMENT_AMOUNT_MISMATCH가발생한다() {
        // given
        User user = 회원_저장(1000L);
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        pointService.reserveUsedPoints(payment);
        testPaymentGateway.success(payment.getPortonePaymentId(), 700L, LocalDateTime.of(2026, 6, 1, 12, 30));

        // when
        // then
        assertThatThrownBy(() -> paymentFacade.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        assertThat(testPaymentGateway.getCallCount()).isEqualTo(1);
        assertThat(testPaymentGateway.getCancelCallCount()).isEqualTo(1);
        assertThat(testPaymentGateway.getCancelPaymentId()).isEqualTo(payment.getPortonePaymentId());
        assertThat(testPaymentGateway.getCancelAmount()).isEqualTo(700L);
        assertThat(testPaymentGateway.getCancelReason()).isEqualTo("PAYMENT_CONFIRM_INTERNAL_FAILURE");
        assertThat(testPaymentGateway.getCancelIdempotencyKey())
                .isEqualTo("payment-confirm-compensation-" + payment.getId());

        Order foundOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(foundPayment.getFailedAt()).isNotNull();
    }

    @Test
    void 결제확정_PortOne승인시각이없으면_EXTERNAL_API_FAILED가발생한다() {
        // given
        User user = 회원_저장(1000L);
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        pointService.reserveUsedPoints(payment);
        testPaymentGateway.success(payment.getPortonePaymentId(), 800L, null);

        // when
        // then
        assertThatThrownBy(() -> paymentFacade.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_API_FAILED);

        assertThat(testPaymentGateway.getCallCount()).isEqualTo(1);
        assertThat(testPaymentGateway.getCancelCallCount()).isEqualTo(1);
    }

    @Test
    void 결제확정_보상취소가실패하면_PAYMENT_COMPENSATION_FAILED가발생하고_내부상태는대기상태로남는다() {
        // given
        User user = 회원_저장();
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        testPaymentGateway.success(payment.getPortonePaymentId(), 700L, LocalDateTime.of(2026, 6, 1, 12, 30));
        testPaymentGateway.failCancel();

        // when
        // then
        assertThatThrownBy(() -> paymentFacade.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_COMPENSATION_FAILED);

        Order foundOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(testPaymentGateway.getCancelCallCount()).isEqualTo(1);
        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void 결제확정_보상취소응답이_SUCCEEDED가아니면_PAYMENT_COMPENSATION_FAILED가발생하고_내부상태는대기상태로남는다() {
        // given
        User user = 회원_저장();
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        testPaymentGateway.success(payment.getPortonePaymentId(), 700L, LocalDateTime.of(2026, 6, 1, 12, 30));
        testPaymentGateway.cancelStatus("REQUESTED");

        // when
        // then
        assertThatThrownBy(() -> paymentFacade.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_COMPENSATION_FAILED);

        Order foundOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(testPaymentGateway.getCancelCallCount()).isEqualTo(1);
        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    private User 회원_저장() {
        return 회원_저장(0L);
    }

    private User 회원_저장(Long pointBalance) {
        User user = User.create(uniqueEmail(), "Password123!", "홍길동", "010-1234-5678");

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
    static class PaymentServiceTestConfig {

        @Bean
        @Primary
        TestPaymentGateway testPaymentGateway() {
            return new TestPaymentGateway();
        }
    }

    static class TestPaymentGateway implements PaymentGateway {

        private PaymentGatewayResponse response;
        private int callCount;
        private int cancelCallCount;
        private String cancelPaymentId;
        private Long cancelAmount;
        private String cancelReason;
        private String cancelIdempotencyKey;
        private boolean cancelFailure;
        private String cancelStatus = "SUCCEEDED";
        private PaymentCancelStatus interpretedCancelStatus = PaymentCancelStatus.SUCCEEDED;

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
            cancelCallCount++;
            this.cancelPaymentId = paymentId;
            this.cancelAmount = cancelAmount;
            this.cancelReason = reason;
            this.cancelIdempotencyKey = idempotencyKey;

            if (cancelFailure) {
                throw new BusinessException(ErrorCode.PAYMENT_COMPENSATION_FAILED);
            }

            return new PaymentCancelResponse("cancel_test", cancelStatus, interpretedCancelStatus);
        }

        void success(String paymentId, Long paidAmount, LocalDateTime approvedAt) {
            response(paymentId, "PAID", paidAmount, approvedAt);
        }

        void response(String paymentId, String status, Long paidAmount, LocalDateTime approvedAt) {
            response = new PaymentGatewayResponse(paymentId, status, paidAmount, approvedAt);
        }

        int getCallCount() {
            return callCount;
        }

        int getCancelCallCount() {
            return cancelCallCount;
        }

        String getCancelPaymentId() {
            return cancelPaymentId;
        }

        Long getCancelAmount() {
            return cancelAmount;
        }

        String getCancelReason() {
            return cancelReason;
        }

        String getCancelIdempotencyKey() {
            return cancelIdempotencyKey;
        }

        void failCancel() {
            cancelFailure = true;
        }

        void cancelStatus(String cancelStatus) {
            this.cancelStatus = cancelStatus;
            this.interpretedCancelStatus = PaymentCancelStatus.RESULT_UNKNOWN;
        }

        void reset() {
            response = null;
            callCount = 0;
            cancelCallCount = 0;
            cancelPaymentId = null;
            cancelAmount = null;
            cancelReason = null;
            cancelIdempotencyKey = null;
            cancelFailure = false;
            cancelStatus = "SUCCEEDED";
            interpretedCancelStatus = PaymentCancelStatus.SUCCEEDED;
        }
    }
}
