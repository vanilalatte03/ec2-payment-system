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
import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentResponse;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentCompensationOutbox;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentStatus;
import com.teamec2.paymentsystem.domain.payment.enums.PaymentCompensationOutboxStatus;
import com.teamec2.paymentsystem.domain.payment.repository.PaymentCompensationOutboxRepository;
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
import com.teamec2.paymentsystem.infra.portone.webhook.repository.PortoneWebhookEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PaymentConfirmTxServiceTest {

    private long sourceCartItemId = 1L;

    @Autowired
    PaymentConfirmTxService paymentConfirmTxService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderItemRepository orderItemRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    PaymentCompensationOutboxRepository paymentCompensationOutboxRepository;

    @Autowired
    PointTransactionRepository pointTransactionRepository;

    @Autowired
    PointService pointService;

    @Autowired
    PortoneWebhookEventRepository webhookEventRepository;

    @Autowired
    CartItemRepository cartItemRepository;

    @Autowired
    CartRepository cartRepository;

    @BeforeEach
    void setUp() {
        clearDatabase();
    }

    @AfterEach
    void tearDown() {
        clearDatabase();
    }

    private void clearDatabase() {
        webhookEventRepository.deleteAll();
        pointTransactionRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        paymentCompensationOutboxRepository.deleteAll();
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 결제완료_장바구니상품을삭제하면_DB에장바구니정리여부를저장한다() {
        // given
        User user = 회원_저장();
        Product product = 상품_저장("후드 집업", 55000, 10);
        Cart cart = cartRepository.save(new Cart(user));
        CartItem cartItem = cartItemRepository.save(new CartItem(cart, product, 1));
        Order order = 주문_저장(user, 55000L, 0L);
        주문상품_저장(order, cartItem);
        Payment payment = 결제_저장(order, 55000L, 0L, 55000L);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);

        // when
        ConfirmPaymentResponse response = paymentConfirmTxService.complete(payment.getId(), approvedAt);

        // then
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(response.cartCleared()).isTrue();
        assertThat(foundPayment.isCartCleared()).isTrue();
        assertThat(cartItemRepository.findById(cartItem.getId())).isEmpty();
    }

    @Test
    void 결제완료_결제전취소된주문상품의_장바구니상품은삭제하지않는다() {
        // given
        User user = 회원_저장();
        Product purchasedProduct = 상품_저장("구매 상품", 55000, 10);
        Product canceledProduct = 상품_저장("결제 전 취소 상품", 24000, 10);
        Cart cart = cartRepository.save(new Cart(user));
        CartItem purchasedCartItem = cartItemRepository.save(new CartItem(cart, purchasedProduct, 1));
        CartItem canceledCartItem = cartItemRepository.save(new CartItem(cart, canceledProduct, 1));
        Order order = 주문_저장(user, 55000L, 0L);
        주문상품_저장(order, purchasedCartItem);
        OrderItem canceledOrderItem = 주문상품_저장(order, canceledCartItem);
        Payment payment = 결제_저장(order, 55000L, 0L, 55000L);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);

        canceledOrderItem.cancel(canceledCartItem.getQuantity(), canceledProduct);
        order.partialCancel();
        orderItemRepository.save(canceledOrderItem);
        orderRepository.save(order);

        // when
        ConfirmPaymentResponse response = paymentConfirmTxService.complete(payment.getId(), approvedAt);

        // then
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(response.cartCleared()).isTrue();
        assertThat(foundPayment.isCartCleared()).isTrue();
        assertThat(cartItemRepository.findById(purchasedCartItem.getId())).isEmpty();
        assertThat(cartItemRepository.findById(canceledCartItem.getId())).isPresent();
    }

    @Test
    void 보상취소성공후_주문결제를실패처리하고_주문상품재고를복구한다() {
        // given
        User user = 회원_저장();
        // 주문 생성 시 이미 재고가 선차감된 상태를 테스트 데이터로 표현한다.
        // 후드 집업은 원래 10개에서 2개 주문되어 현재 8개가 남은 상황이다.
        Product firstProduct = 상품_저장("후드 집업", 55000, 8);
        // 볼캡은 원래 10개에서 3개 주문되어 현재 7개가 남은 상황이다.
        Product secondProduct = 상품_저장("볼캡", 24000, 7);
        Order order = 주문_저장(user, 158000L, 0L);
        주문상품_저장(order, firstProduct, 2);
        주문상품_저장(order, secondProduct, 3);
        Payment payment = 결제_저장(order, 158000L, 0L, 158000L);

        // when
        paymentConfirmTxService.failAfterCompensation(payment.getId());

        // then
        Order foundOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        Product foundFirstProduct = productRepository.findById(firstProduct.getId()).orElseThrow();
        Product foundSecondProduct = productRepository.findById(secondProduct.getId()).orElseThrow();

        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(foundPayment.getFailedAt()).isNotNull();
        assertThat(foundFirstProduct.getStock()).isEqualTo(10);
        assertThat(foundSecondProduct.getStock()).isEqualTo(10);
    }

    @Test
    void 보상취소성공후_예약차감한포인트를복구하고_취소원장을기록한다() {
        // given
        User user = 회원_저장(1000L);
        // 주문 생성 시 상품 1개가 선차감되어 현재 재고는 4개만 남아 있다.
        Product product = 상품_저장("후드 집업", 55000, 4);
        Order order = 주문_저장(user, 1000L, 200L);
        주문상품_저장(order, product, 1);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        pointService.reserveUsedPoints(payment);

        User reservedUser = userRepository.findById(user.getId()).orElseThrow();
        String reserveKey = PointTransaction.paymentIdempotencyKey(payment, PointTransactionType.USE_RESERVE);
        String cancelKey = PointTransaction.paymentIdempotencyKey(payment, PointTransactionType.USE_CANCEL);

        assertThat(reservedUser.getPointBalance()).isEqualTo(800L);
        assertThat(pointTransactionRepository.existsByIdempotencyKey(reserveKey)).isTrue();

        // when
        paymentConfirmTxService.failAfterCompensation(payment.getId());

        // then
        User foundUser = userRepository.findById(user.getId()).orElseThrow();
        Order foundOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        Product foundProduct = productRepository.findById(product.getId()).orElseThrow();
        PointTransaction cancelTransaction = pointTransactionRepository.findByIdempotencyKey(cancelKey).orElseThrow();

        assertThat(foundUser.getPointBalance()).isEqualTo(1000L);
        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(foundProduct.getStock()).isEqualTo(5);
        assertThat(cancelTransaction.getType()).isEqualTo(PointTransactionType.USE_CANCEL);
        assertThat(cancelTransaction.getAmount()).isEqualTo(200L);
    }

    @Test
    void 보상취소아웃박스가_처리중이면_중복보상요청으로_PENDING으로돌리지않는다() {
        // given
        User user = 회원_저장();
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        Long outboxId = paymentConfirmTxService.markCompensationRequired(
                payment.getId(),
                800L,
                "첫 보상 요청"
        );

        paymentConfirmTxService.startCompensation(outboxId);
        PaymentCompensationOutbox processingOutbox =
                paymentCompensationOutboxRepository.findById(outboxId).orElseThrow();
        LocalDateTime processingStartedAt = processingOutbox.getProcessingStartedAt();

        // when
        Long duplicatedOutboxId = paymentConfirmTxService.markCompensationRequired(
                payment.getId(),
                800L,
                "중복 보상 요청"
        );

        // then
        PaymentCompensationOutbox foundOutbox =
                paymentCompensationOutboxRepository.findById(outboxId).orElseThrow();

        assertThat(duplicatedOutboxId).isEqualTo(outboxId);
        assertThat(foundOutbox.getStatus()).isEqualTo(PaymentCompensationOutboxStatus.PROCESSING);
        assertThat(foundOutbox.getProcessingStartedAt()).isEqualTo(processingStartedAt);
        assertThat(foundOutbox.getLastErrorMessage()).isEqualTo("첫 보상 요청");
    }

    @Test
    void 완료재시도_최대횟수를초과하면_보상취소대상으로전환한다() {
        // given
        User user = 회원_저장();
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);

        payment.markConfirmRetryRequired(approvedAt, "DB 완료 실패", approvedAt.plusMinutes(1));
        for (int i = 0; i < 5; i++) {
            payment.markConfirmRetry("일시 장애", approvedAt.plusMinutes(2 + i));
        }
        paymentRepository.saveAndFlush(payment);

        // when
        paymentConfirmTxService.retryConfirm(payment.getId(), "계속 실패");

        // then
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        PaymentCompensationOutbox outbox =
                paymentCompensationOutboxRepository.findByPaymentId(payment.getId()).orElseThrow();

        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.COMPENSATION_REQUIRED);
        assertThat(foundPayment.getNextConfirmAttemptAt()).isNull();
        assertThat(foundPayment.getConfirmProcessingStartedAt()).isNull();
        assertThat(foundPayment.getConfirmRetryApprovedAt()).isNull();
        assertThat(outbox.getStatus()).isEqualTo(PaymentCompensationOutboxStatus.PENDING);
        assertThat(outbox.getCancelAmount()).isEqualTo(800L);
        assertThat(outbox.getLastErrorMessage())
                .contains("내부 결제 완료 재시도 횟수를 초과했습니다.")
                .contains("계속 실패");
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

    private Product 상품_저장(String name, int price, int stock) {
        return productRepository.save(new Product(
                name,
                price,
                stock,
                "테스트 상품",
                ProductStatus.ON_SALE,
                ProductCategory.TOP
        ));
    }

    private Order 주문_저장(User user, Long totalAmount, Long usedPointAmount) {
        return orderRepository.save(Order.create(user, uniqueOrderNumber(), totalAmount, usedPointAmount));
    }

    private OrderItem 주문상품_저장(Order order, Product product, int quantity) {
        return orderItemRepository.save(new OrderItem(order, product, sourceCartItemId++, quantity));
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
}
