package com.teamec2.paymentsystem.domain.payment.service;

import com.teamec2.paymentsystem.domain.cart.repository.CartItemRepository;
import com.teamec2.paymentsystem.domain.cart.repository.CartRepository;
import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.order.entity.OrderStatus;
import com.teamec2.paymentsystem.domain.order.repository.OrderItemRepository;
import com.teamec2.paymentsystem.domain.order.repository.OrderRepository;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentStatus;
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
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 보상취소성공후_주문결제를실패처리하고_주문상품재고를복구한다() {
        // given
        User user = 회원_저장();
        Product firstProduct = 상품_저장("후드 집업", 55000, 8);
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
    void 보상취소성공후_이미취소된주문상품은_재고를다시복구하지않는다() {
        // given
        User user = 회원_저장();
        Product canceledProduct = 상품_저장("이미 취소한 상품", 55000, 8);
        Product remainingProduct = 상품_저장("남아있는 상품", 24000, 7);
        Order order = 주문_저장(user, 158000L, 0L);
        OrderItem canceledOrderItem = 주문상품_저장(order, canceledProduct, 2);
        주문상품_저장(order, remainingProduct, 3);
        Payment payment = 결제_저장(order, 158000L, 0L, 158000L);

        canceledOrderItem.cancel();
        orderItemRepository.saveAndFlush(canceledOrderItem);
        productRepository.saveAndFlush(canceledProduct);

        assertThat(productRepository.findById(canceledProduct.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(productRepository.findById(remainingProduct.getId()).orElseThrow().getStock()).isEqualTo(7);

        // when
        paymentConfirmTxService.failAfterCompensation(payment.getId());

        // then
        Order foundOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        Product foundCanceledProduct = productRepository.findById(canceledProduct.getId()).orElseThrow();
        Product foundRemainingProduct = productRepository.findById(remainingProduct.getId()).orElseThrow();

        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(foundCanceledProduct.getStock()).isEqualTo(10);
        assertThat(foundRemainingProduct.getStock()).isEqualTo(10);
    }

    @Test
    void 보상취소성공후_예약차감한포인트를복구하고_취소원장을기록한다() {
        // given
        User user = 회원_저장(1000L);
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
