package com.teamec2.paymentsystem.domain.order.service;

import com.teamec2.paymentsystem.domain.cart.entity.Cart;
import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.cart.repository.CartItemRepository;
import com.teamec2.paymentsystem.domain.cart.repository.CartRepository;
import com.teamec2.paymentsystem.domain.order.dto.CancelOrderResponse;
import com.teamec2.paymentsystem.domain.order.dto.CreateOrderResponse;
import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.order.entity.OrderItemStatus;
import com.teamec2.paymentsystem.domain.order.entity.OrderStatus;
import com.teamec2.paymentsystem.domain.order.repository.OrderItemRepository;
import com.teamec2.paymentsystem.domain.order.repository.OrderRepository;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentStatus;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentType;
import com.teamec2.paymentsystem.domain.payment.dto.PaymentCancelResponse;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGateway;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGatewayResponse;
import com.teamec2.paymentsystem.domain.payment.repository.PaymentRepository;
import com.teamec2.paymentsystem.domain.point.entity.PointTransaction;
import com.teamec2.paymentsystem.domain.point.enums.PointTransactionType;
import com.teamec2.paymentsystem.domain.point.repository.PointTransactionRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OrderServiceTest {

    @Autowired
    OrderService orderService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    CartRepository cartRepository;

    @Autowired
    CartItemRepository cartItemRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderItemRepository orderItemRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    PointTransactionRepository pointTransactionRepository;

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
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 주문생성_선택상품이면_주문결제주문상품을생성하고_재고와포인트를예약한다() {
        // given
        User user = 회원_저장(10000L);
        Product selectedProduct = 상품_저장("선택 상품", 30000, 10);
        Product notSelectedProduct = 상품_저장("선택하지 않은 상품", 20000, 10);
        CartItem selectedCartItem = 장바구니상품_저장(user, selectedProduct, 2);
        장바구니상품_저장(user, notSelectedProduct, 1);

        // when
        CreateOrderResponse response = orderService.createOrder(
                user.getId(),
                List.of(selectedCartItem.getId()),
                5000L
        );

        // then
        Order order = orderRepository.findAll().get(0);
        Payment payment = paymentRepository.findAll().get(0);
        OrderItem orderItem = orderItemRepository.findAll().get(0);
        PointTransaction pointTransaction = pointTransactionRepository.findAll().get(0);

        assertThat(response.order().orderId()).isEqualTo(order.getId());
        assertThat(response.order().status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(response.payment().paymentId()).isEqualTo(payment.getId());
        assertThat(response.payment().type()).isEqualTo(PaymentType.POINT_CARD);
        assertThat(response.nextAction()).isEqualTo("OPEN_PORTONE_PAYMENT");

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderItemRepository.count()).isEqualTo(1);
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(cartItemRepository.count()).isEqualTo(2);

        assertThat(order.getTotalAmount()).isEqualTo(60000L);
        assertThat(order.getUsedPoint()).isEqualTo(5000L);
        assertThat(orderItem.getSourceCartItemId()).isEqualTo(selectedCartItem.getId());
        assertThat(orderItem.getProductName()).isEqualTo("선택 상품");
        assertThat(orderItem.getPrice()).isEqualTo(30000);
        assertThat(orderItem.getQuantity()).isEqualTo(2);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getTotalAmount()).isEqualTo(60000L);
        assertThat(payment.getUsedPointAmount()).isEqualTo(5000L);
        assertThat(payment.getPgAmount()).isEqualTo(55000L);
        assertThat(payment.getRewardPointAmount()).isEqualTo(550L);

        assertThat(productRepository.findById(selectedProduct.getId()).orElseThrow().getStock()).isEqualTo(8);
        assertThat(productRepository.findById(notSelectedProduct.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isEqualTo(5000L);
        assertThat(pointTransaction.getType()).isEqualTo(PointTransactionType.USE_RESERVE);
        assertThat(pointTransaction.getAmount()).isEqualTo(5000L);
    }

    @Test
    void 주문생성_재고부족이면_주문결제주문상품을저장하지않고_재고도롤백한다() {
        // given
        User user = 회원_저장(0L);
        Product enoughStockProduct = 상품_저장("재고 충분 상품", 10000, 10);
        Product shortageProduct = 상품_저장("재고 부족 상품", 20000, 1);
        CartItem enoughStockCartItem = 장바구니상품_저장(user, enoughStockProduct, 2);
        CartItem shortageCartItem = 장바구니상품_저장(user, shortageProduct, 2);

        // when
        // then
        assertThatThrownBy(() -> orderService.createOrder(
                user.getId(),
                List.of(enoughStockCartItem.getId(), shortageCartItem.getId()),
                0L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_STOCK_SHORTAGE);

        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
        assertThat(paymentRepository.count()).isZero();
        assertThat(pointTransactionRepository.count()).isZero();
        assertThat(productRepository.findById(enoughStockProduct.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(productRepository.findById(shortageProduct.getId()).orElseThrow().getStock()).isEqualTo(1);
    }

    @Test
    void 주문취소_일부상품취소로_사용포인트가줄어들면_금액을재계산하고_포인트와재고를복구한다() {
        // given
        User user = 회원_저장(40000L);
        Product remainingProduct = 상품_저장("남길 상품", 30000, 10);
        Product cancelProduct = 상품_저장("취소 상품", 20000, 10);
        장바구니상품_저장(user, remainingProduct, 1);
        장바구니상품_저장(user, cancelProduct, 1);

        orderService.createOrder(user.getId(), null, 40000L);

        Order order = orderRepository.findAll().get(0);
        Payment payment = paymentRepository.findAll().get(0);
        OrderItem cancelOrderItem = orderItemRepository.findAll().stream()
                .filter(orderItem -> orderItem.getProductId().equals(cancelProduct.getId()))
                .findFirst()
                .orElseThrow();

        // when
        CancelOrderResponse response = orderService.cancelOrder(
                user.getId(),
                order.getId(),
                List.of(cancelOrderItem.getId())
        );

        // then
        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        OrderItem updatedCancelOrderItem = orderItemRepository.findById(cancelOrderItem.getId()).orElseThrow();
        List<PointTransaction> pointTransactions = pointTransactionRepository.findAll();

        assertThat(response.previousOrderStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(response.currentOrderStatus()).isEqualTo(OrderStatus.PARTIAL_CANCELED);
        assertThat(response.canceledAmount()).isEqualTo(20000L);
        assertThat(response.remainingTotalAmount()).isEqualTo(30000L);
        assertThat(response.restoredPointAmount()).isEqualTo(10000L);
        assertThat(response.remainingUsedPointAmount()).isEqualTo(30000L);
        assertThat(response.remainingPgAmount()).isZero();
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PENDING);

        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PARTIAL_CANCELED);
        assertThat(updatedOrder.getTotalAmount()).isEqualTo(30000L);
        assertThat(updatedOrder.getUsedPoint()).isEqualTo(30000L);
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(updatedPayment.getPaymentType()).isEqualTo(PaymentType.POINT_ONLY);
        assertThat(updatedPayment.getTotalAmount()).isEqualTo(30000L);
        assertThat(updatedPayment.getUsedPointAmount()).isEqualTo(30000L);
        assertThat(updatedPayment.getPgAmount()).isZero();
        assertThat(updatedPayment.getRewardPointAmount()).isZero();
        assertThat(updatedCancelOrderItem.getStatus()).isEqualTo(OrderItemStatus.CANCELED);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isEqualTo(10000L);
        assertThat(productRepository.findById(remainingProduct.getId()).orElseThrow().getStock()).isEqualTo(9);
        assertThat(productRepository.findById(cancelProduct.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(pointTransactions)
                .extracting(PointTransaction::getType)
                .containsExactlyInAnyOrder(PointTransactionType.USE_RESERVE, PointTransactionType.USE_CANCEL);
    }

    @Test
    void 주문취소_다른회원주문이면_ORDER_ACCESS_DENIED가발생하고_상태를바꾸지않는다() {
        // given
        User owner = 회원_저장(0L);
        User otherUser = 회원_저장(0L);
        Product product = 상품_저장("권한 검증 상품", 10000, 10);
        장바구니상품_저장(owner, product, 1);

        orderService.createOrder(owner.getId(), null, 0L);

        Order order = orderRepository.findAll().get(0);

        // when
        // then
        assertThatThrownBy(() -> orderService.cancelOrder(otherUser.getId(), order.getId(), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_ACCESS_DENIED);

        Order foundOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment foundPayment = paymentRepository.findAll().get(0);
        OrderItem foundOrderItem = orderItemRepository.findAll().get(0);

        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(foundOrderItem.getStatus()).isEqualTo(OrderItemStatus.ORDERED);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(9);
    }

    @Test
    void 주문취소_PG결제가이미성공했으면_PG취소후_내부주문을취소한다() {
        // given
        User user = 회원_저장(0L);
        Product product = 상품_저장("PG 취소 상품", 10000, 10);
        장바구니상품_저장(user, product, 1);

        orderService.createOrder(user.getId(), null, 0L);

        Order order = orderRepository.findAll().get(0);
        Payment payment = paymentRepository.findAll().get(0);
        testPaymentGateway.paid(payment.getPortonePaymentId(), payment.getPgAmount());

        // when
        CancelOrderResponse response = orderService.cancelOrder(user.getId(), order.getId(), null);

        // then
        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(response.currentOrderStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(testPaymentGateway.getPaymentCallCount()).isEqualTo(1);
        assertThat(testPaymentGateway.getCancelCallCount()).isEqualTo(1);
        assertThat(testPaymentGateway.getCancelPaymentId()).isEqualTo(payment.getPortonePaymentId());
        assertThat(testPaymentGateway.getCancelAmount()).isEqualTo(payment.getPgAmount());
        assertThat(testPaymentGateway.getCancelReason()).isEqualTo("ORDER_CANCEL_BEFORE_INTERNAL_CONFIRM");
        assertThat(testPaymentGateway.getCancelIdempotencyKey()).isEqualTo("order-cancel-" + payment.getId());
    }

    @Test
    void 주문취소_PG결제가이미성공했고_일부취소이면_직접취소하지않는다() {
        // given
        User user = 회원_저장(0L);
        Product remainingProduct = 상품_저장("남길 상품", 30000, 10);
        Product cancelProduct = 상품_저장("취소 상품", 20000, 10);
        장바구니상품_저장(user, remainingProduct, 1);
        장바구니상품_저장(user, cancelProduct, 1);

        orderService.createOrder(user.getId(), null, 0L);

        Order order = orderRepository.findAll().get(0);
        Payment payment = paymentRepository.findAll().get(0);
        OrderItem cancelOrderItem = orderItemRepository.findAll().stream()
                .filter(orderItem -> orderItem.getProductId().equals(cancelProduct.getId()))
                .findFirst()
                .orElseThrow();
        testPaymentGateway.paid(payment.getPortonePaymentId(), payment.getPgAmount());

        // when
        // then
        assertThatThrownBy(() -> orderService.cancelOrder(user.getId(), order.getId(), List.of(cancelOrderItem.getId())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFUND_NOT_ALLOWED);

        Order foundOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        OrderItem foundCancelOrderItem = orderItemRepository.findById(cancelOrderItem.getId()).orElseThrow();

        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(foundCancelOrderItem.getStatus()).isEqualTo(OrderItemStatus.ORDERED);
        assertThat(productRepository.findById(remainingProduct.getId()).orElseThrow().getStock()).isEqualTo(9);
        assertThat(productRepository.findById(cancelProduct.getId()).orElseThrow().getStock()).isEqualTo(9);
        assertThat(testPaymentGateway.getPaymentCallCount()).isEqualTo(1);
        assertThat(testPaymentGateway.getCancelCallCount()).isZero();
    }

    private User 회원_저장(Long pointBalance) {
        User user = User.create(uniqueEmail(), "Password123!", "홍길동", "010-1234-5678");

        if (pointBalance > 0) {
            user.increasePointBalance(pointBalance);
        }

        User savedUser = userRepository.save(user);
        cartRepository.save(new Cart(savedUser));
        return savedUser;
    }

    private Product 상품_저장(String name, int price, int stock) {
        return productRepository.save(new Product(
                name,
                price,
                stock,
                "테스트 상품입니다.",
                ProductStatus.ON_SALE,
                ProductCategory.TOP
        ));
    }

    private CartItem 장바구니상품_저장(User user, Product product, int quantity) {
        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow();
        return cartItemRepository.save(new CartItem(cart, product, quantity));
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    @TestConfiguration
    static class OrderServiceTestConfig {

        @Bean
        @Primary
        TestPaymentGateway testPaymentGateway() {
            return new TestPaymentGateway();
        }
    }

    static class TestPaymentGateway implements PaymentGateway {

        private PaymentGatewayResponse response;
        private int paymentCallCount;
        private int cancelCallCount;
        private String cancelPaymentId;
        private Long cancelAmount;
        private String cancelReason;
        private String cancelIdempotencyKey;

        @Override
        public PaymentGatewayResponse getPayment(String paymentId) {
            paymentCallCount++;

            if (response == null) {
                return new PaymentGatewayResponse(paymentId, "READY", null, null);
            }

            return response;
        }

        @Override
        public PaymentCancelResponse cancelPayment(
                String paymentId,
                Long cancelAmount,
                String reason,
                String idempotencyKey
        ) {
            cancelCallCount++;
            this.cancelPaymentId = paymentId;
            this.cancelAmount = cancelAmount;
            this.cancelReason = reason;
            this.cancelIdempotencyKey = idempotencyKey;

            return new PaymentCancelResponse("cancel_test", "SUCCEEDED");
        }

        void paid(String paymentId, Long paidAmount) {
            response = new PaymentGatewayResponse(
                    paymentId,
                    "PAID",
                    paidAmount,
                    LocalDateTime.of(2026, 6, 5, 12, 0)
            );
        }

        int getPaymentCallCount() {
            return paymentCallCount;
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

        void reset() {
            response = null;
            paymentCallCount = 0;
            cancelCallCount = 0;
            cancelPaymentId = null;
            cancelAmount = null;
            cancelReason = null;
            cancelIdempotencyKey = null;
        }
    }
}
