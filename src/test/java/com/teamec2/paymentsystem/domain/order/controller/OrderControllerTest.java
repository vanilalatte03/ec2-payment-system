package com.teamec2.paymentsystem.domain.order.controller;

import com.teamec2.paymentsystem.domain.cart.entity.Cart;
import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.cart.repository.CartItemRepository;
import com.teamec2.paymentsystem.domain.cart.repository.CartRepository;
import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.order.entity.OrderItemStatus;
import com.teamec2.paymentsystem.domain.order.entity.OrderStatus;
import com.teamec2.paymentsystem.domain.order.repository.OrderItemRepository;
import com.teamec2.paymentsystem.domain.order.repository.OrderRepository;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentStatus;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentType;
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
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import com.teamec2.paymentsystem.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    private static final int BODY_STATUS = 200;

    @Autowired
    MockMvc mockMvc;

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
    JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        clearDatabase();
    }

    @AfterEach
    void tearDown() {
        clearDatabase();
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
    void 주문서미리보기_cartItemIds가있으면_선택상품만_주문형태로반환한다() throws Exception {
        // given
        User user = 회원_저장(5000L);
        Product selectedProduct = 상품_저장("오버핏 티셔츠", 39000, 10, ProductStatus.ON_SALE);
        Product notSelectedProduct = 상품_저장("와이드 팬츠", 68000, 5, ProductStatus.ON_SALE);
        CartItem selectedCartItem = 장바구니상품_저장(user, selectedProduct, 2);
        장바구니상품_저장(user, notSelectedProduct, 1);

        // when
        // then
        mockMvc.perform(get("/api/orders/preview")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .param("cartItemIds", selectedCartItem.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].cartItemId").value(selectedCartItem.getId()))
                .andExpect(jsonPath("$.data.items[0].productId").value(selectedProduct.getId()))
                .andExpect(jsonPath("$.data.items[0].productName").value("오버핏 티셔츠"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(39000))
                .andExpect(jsonPath("$.data.items[0].lineAmount").value(78000))
                .andExpect(jsonPath("$.data.items[0].stock").value(10))
                .andExpect(jsonPath("$.data.items[0].status").value(ProductStatus.ON_SALE.name()))
                .andExpect(jsonPath("$.data.totalQuantity").value(2))
                .andExpect(jsonPath("$.data.totalAmount").value(78000));

        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
        assertThat(paymentRepository.count()).isZero();
        assertThat(productRepository.findById(selectedProduct.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(productRepository.findById(notSelectedProduct.getId()).orElseThrow().getStock()).isEqualTo(5);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isEqualTo(5000L);
        assertThat(pointTransactionRepository.count()).isZero();
    }

    @Test
    void 내주문내역조회_본인주문목록을_최신순으로반환한다() throws Exception {
        // given
        User user = 회원_저장(0L);
        User otherUser = 회원_저장(0L);
        Order oldOrder = orderRepository.save(Order.create(user, "ORD-TEST-OLD", 10000L, 0L));
        Order latestOrder = orderRepository.save(Order.create(user, "ORD-TEST-LATEST", 20000L, 0L));
        orderRepository.save(Order.create(otherUser, "ORD-TEST-OTHER", 30000L, 0L));

        // when
        // then
        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + accessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.message").value("요청이 성공했습니다."))
                .andExpect(jsonPath("$.data.orders.length()").value(2))
                .andExpect(jsonPath("$.data.orders[0].orderId").value(latestOrder.getId()))
                .andExpect(jsonPath("$.data.orders[0].orderNumber").value("ORD-TEST-LATEST"))
                .andExpect(jsonPath("$.data.orders[0].status").value(OrderStatus.PAYMENT_PENDING.name()))
                .andExpect(jsonPath("$.data.orders[0].totalAmount").value(20000))
                .andExpect(jsonPath("$.data.orders[0].orderedAt").exists())
                .andExpect(jsonPath("$.data.orders[1].orderId").value(oldOrder.getId()))
                .andExpect(jsonPath("$.data.orders[1].orderNumber").value("ORD-TEST-OLD"))
                .andExpect(jsonPath("$.data.orders[1].status").value(OrderStatus.PAYMENT_PENDING.name()))
                .andExpect(jsonPath("$.data.orders[1].totalAmount").value(10000))
                .andExpect(jsonPath("$.data.orders[1].orderedAt").exists());
    }

    @Test
    void 주문상세조회_본인주문이면_주문상품스냅샷과_결제포인트요약을반환한다() throws Exception {
        // given
        User user = 회원_저장(10000L);
        Product product = 상품_저장("상세 조회 상품", 30000, 10, ProductStatus.ON_SALE);
        CartItem cartItem = 장바구니상품_저장(user, product, 2);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [%d],
                                  "usedPointAmount": 5000
                                }
                                """.formatted(cartItem.getId())))
                .andExpect(status().isCreated());

        Order order = orderRepository.findAll().get(0);
        Payment payment = paymentRepository.findAll().get(0);
        OrderItem orderItem = orderItemRepository.findAll().get(0);

        // when
        // then
        mockMvc.perform(get("/api/orders/{orderId}", order.getId())
                        .header("Authorization", "Bearer " + accessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.message").value("요청이 성공했습니다."))
                .andExpect(jsonPath("$.data.order.orderId").value(order.getId()))
                .andExpect(jsonPath("$.data.order.orderNumber").value(order.getOrderNumber()))
                .andExpect(jsonPath("$.data.order.status").value(OrderStatus.PAYMENT_PENDING.name()))
                .andExpect(jsonPath("$.data.order.totalAmount").value(60000))
                .andExpect(jsonPath("$.data.order.usedPointAmount").value(5000))
                .andExpect(jsonPath("$.data.order.orderedAt").exists())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].orderItemId").value(orderItem.getId()))
                .andExpect(jsonPath("$.data.items[0].productId").value(product.getId()))
                .andExpect(jsonPath("$.data.items[0].productName").value("상세 조회 상품"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].refundedQuantity").value(0))
                .andExpect(jsonPath("$.data.items[0].status").value(OrderItemStatus.ORDERED.name()))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(30000))
                .andExpect(jsonPath("$.data.items[0].lineAmount").value(60000))
                .andExpect(jsonPath("$.data.payment.paymentId").value(payment.getId()))
                .andExpect(jsonPath("$.data.payment.portonePaymentId").value(payment.getPortonePaymentId()))
                .andExpect(jsonPath("$.data.payment.status").value(PaymentStatus.PENDING.name()))
                .andExpect(jsonPath("$.data.payment.type").value(PaymentType.POINT_CARD.name()))
                .andExpect(jsonPath("$.data.payment.totalAmount").value(60000))
                .andExpect(jsonPath("$.data.payment.usedPointAmount").value(5000))
                .andExpect(jsonPath("$.data.payment.pgAmount").value(55000))
                .andExpect(jsonPath("$.data.payment.rewardPointAmount").value(550))
                .andExpect(jsonPath("$.data.pointSummary.usedPointAmount").value(5000))
                .andExpect(jsonPath("$.data.pointSummary.rewardPointAmount").value(550));
    }

    @Test
    void 주문상세조회_타인주문이면_ORDER_ACCESS_DENIED를반환한다() throws Exception {
        // given
        User owner = 회원_저장(0L);
        User otherUser = 회원_저장(0L);
        Product product = 상품_저장("타인 주문 상품", 10000, 10, ProductStatus.ON_SALE);
        CartItem cartItem = 장바구니상품_저장(owner, product, 1);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [%d],
                                  "usedPointAmount": 0
                                }
                                """.formatted(cartItem.getId())))
                .andExpect(status().isCreated());

        Order order = orderRepository.findAll().get(0);

        // when
        // then
        mockMvc.perform(get("/api/orders/{orderId}", order.getId())
                        .header("Authorization", "Bearer " + accessToken(otherUser)))
                .andExpect(status().is(ErrorCode.ORDER_ACCESS_DENIED.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.ORDER_ACCESS_DENIED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ORDER_ACCESS_DENIED.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 주문서미리보기_cartItemIds가없으면_장바구니전체를_현재가로반환하고_주문을생성하지않는다() throws Exception {
        // given
        User user = 회원_저장(0L);
        Product firstProduct = 상품_저장("후드 집업", 55000, 10, ProductStatus.ON_SALE);
        Product secondProduct = 상품_저장("볼캡", 24000, 10, ProductStatus.ON_SALE);
        장바구니상품_저장(user, firstProduct, 1);
        장바구니상품_저장(user, secondProduct, 2);

        // when
        // then
        mockMvc.perform(get("/api/orders/preview")
                        .header("Authorization", "Bearer " + accessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.message").value("요청이 성공했습니다."))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.totalQuantity").value(3))
                .andExpect(jsonPath("$.data.totalAmount").value(103000));

        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
        assertThat(paymentRepository.count()).isZero();
        assertThat(productRepository.findById(firstProduct.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(productRepository.findById(secondProduct.getId()).orElseThrow().getStock()).isEqualTo(10);
    }

    @Test
    void 주문생성_선택장바구니상품이면_주문결제주문상품을생성하고_재고를차감한다() throws Exception {
        // given
        User user = 회원_저장(10000L);
        Product selectedProduct = 상품_저장("오버핏 티셔츠", 39000, 10, ProductStatus.ON_SALE);
        Product notSelectedProduct = 상품_저장("와이드 팬츠", 68000, 5, ProductStatus.ON_SALE);
        CartItem selectedCartItem = 장바구니상품_저장(user, selectedProduct, 2);
        장바구니상품_저장(user, notSelectedProduct, 1);

        // when
        // then
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
	                                {
	                                  "cartItemIds": [%d],
	                                  "usedPointAmount": 5000
	                                }
	                                """.formatted(selectedCartItem.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.message").value("요청이 성공했습니다."))
                .andExpect(jsonPath("$.data.order.orderId").isNumber())
                .andExpect(jsonPath("$.data.order.orderNumber").exists())
                .andExpect(jsonPath("$.data.order.status").value(OrderStatus.PAYMENT_PENDING.name()))
                .andExpect(jsonPath("$.data.payment.paymentId").isNumber())
                .andExpect(jsonPath("$.data.payment.portonePaymentId").exists())
                .andExpect(jsonPath("$.data.payment.status").value(PaymentStatus.PENDING.name()))
	                .andExpect(jsonPath("$.data.payment.type").value(PaymentType.POINT_CARD.name()))
	                .andExpect(jsonPath("$.data.order.totalAmount").value(78000))
	                .andExpect(jsonPath("$.data.payment.usedPointAmount").value(5000))
	                .andExpect(jsonPath("$.data.payment.pgAmount").value(73000))
                .andExpect(jsonPath("$.data.nextAction").value("OPEN_PORTONE_PAYMENT"))
                .andExpect(jsonPath("$.data.order.items.length()").value(1))
                .andExpect(jsonPath("$.data.order.items[0].orderItemId").isNumber())
	                .andExpect(jsonPath("$.data.order.items[0].productId").value(selectedProduct.getId()))
	                .andExpect(jsonPath("$.data.order.items[0].productName").value("오버핏 티셔츠"))
	                .andExpect(jsonPath("$.data.order.items[0].quantity").value(2))
	                .andExpect(jsonPath("$.data.order.items[0].unitPrice").value(39000))
	                .andExpect(jsonPath("$.data.order.items[0].lineAmount").value(78000));

        List<Order> orders = orderRepository.findAll();
        List<OrderItem> orderItems = orderItemRepository.findAll();
        List<Payment> payments = paymentRepository.findAll();
        List<PointTransaction> pointTransactions = pointTransactionRepository.findAll();

        assertThat(orders).hasSize(1);
        assertThat(orderItems).hasSize(1);
        assertThat(payments).hasSize(1);
        assertThat(orderItems.get(0).getSourceCartItemId()).isEqualTo(selectedCartItem.getId());
        assertThat(orders.get(0).getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payments.get(0).getPaymentType()).isEqualTo(PaymentType.POINT_CARD);
        assertThat(payments.get(0).getRewardPointAmount()).isEqualTo(730L);
        assertThat(productRepository.findById(selectedProduct.getId()).orElseThrow().getStock()).isEqualTo(8);
        assertThat(productRepository.findById(notSelectedProduct.getId()).orElseThrow().getStock()).isEqualTo(5);
        assertThat(cartItemRepository.findAll()).hasSize(2);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isEqualTo(5000L);
        assertThat(pointTransactions).hasSize(1);
        assertThat(pointTransactions.get(0).getType()).isEqualTo(PointTransactionType.USE_RESERVE);
        assertThat(pointTransactions.get(0).getAmount()).isEqualTo(5000L);
        assertThat(pointTransactions.get(0).getIdempotencyKey())
                .isEqualTo("PAYMENT:%d:USE_RESERVE".formatted(payments.get(0).getId()));
    }

    @Test
    void 주문생성_cartItemIds가없으면_장바구니전체상품으로주문을생성한다() throws Exception {
        // given
        User user = 회원_저장(0L);
        Product firstProduct = 상품_저장("후드 집업", 55000, 10, ProductStatus.ON_SALE);
        Product secondProduct = 상품_저장("볼캡", 24000, 10, ProductStatus.ON_SALE);
        CartItem firstCartItem = 장바구니상품_저장(user, firstProduct, 1);
        CartItem secondCartItem = 장바구니상품_저장(user, secondProduct, 2);

        // when
        // then
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
	                                {
	                                  "usedPointAmount": 0
	                                }
	                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.data.order.status").value(OrderStatus.PAYMENT_PENDING.name()))
                .andExpect(jsonPath("$.data.payment.status").value(PaymentStatus.PENDING.name()))
	                .andExpect(jsonPath("$.data.payment.type").value(PaymentType.CARD.name()))
	                .andExpect(jsonPath("$.data.order.totalAmount").value(103000))
	                .andExpect(jsonPath("$.data.payment.usedPointAmount").value(0))
	                .andExpect(jsonPath("$.data.payment.pgAmount").value(103000))
                .andExpect(jsonPath("$.data.nextAction").value("OPEN_PORTONE_PAYMENT"))
                .andExpect(jsonPath("$.data.order.items.length()").value(2));

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderItemRepository.count()).isEqualTo(2);
        assertThat(orderItemRepository.findAll())
                .extracting(OrderItem::getSourceCartItemId)
                .containsExactlyInAnyOrder(firstCartItem.getId(), secondCartItem.getId());
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(paymentRepository.findAll().get(0).getRewardPointAmount()).isEqualTo(1030L);
        assertThat(productRepository.findById(firstProduct.getId()).orElseThrow().getStock()).isEqualTo(9);
        assertThat(productRepository.findById(secondProduct.getId()).orElseThrow().getStock()).isEqualTo(8);
        assertThat(cartItemRepository.findAll()).hasSize(2);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isZero();
        assertThat(pointTransactionRepository.count()).isZero();
    }

    @Test
    void 주문생성_포인트전액결제이면_nextAction은_CONFIRM_POINT_ONLY다() throws Exception {
        // given
        User user = 회원_저장(78000L);
        Product product = 상품_저장("스니커즈", 39000, 10, ProductStatus.ON_SALE);
        CartItem cartItem = 장바구니상품_저장(user, product, 2);

        // when
        // then
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
	                                {
	                                  "cartItemIds": [%d],
	                                  "usedPointAmount": 78000
	                                }
	                                """.formatted(cartItem.getId())))
                .andExpect(status().isCreated())
	                .andExpect(jsonPath("$.data.payment.type").value(PaymentType.POINT_ONLY.name()))
	                .andExpect(jsonPath("$.data.order.totalAmount").value(78000))
	                .andExpect(jsonPath("$.data.payment.usedPointAmount").value(78000))
	                .andExpect(jsonPath("$.data.payment.pgAmount").value(0))
	                .andExpect(jsonPath("$.data.nextAction").value("CONFIRM_POINT_ONLY"));

            List<Payment> payments = paymentRepository.findAll();
            List<PointTransaction> pointTransactions = pointTransactionRepository.findAll();

            assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isZero();
            assertThat(payments.get(0).getRewardPointAmount()).isZero();
            assertThat(pointTransactions).hasSize(1);
            assertThat(pointTransactions.get(0).getType()).isEqualTo(PointTransactionType.USE_RESERVE);
            assertThat(pointTransactions.get(0).getAmount()).isEqualTo(78000L);
            assertThat(pointTransactions.get(0).getIdempotencyKey())
                    .isEqualTo("PAYMENT:%d:USE_RESERVE".formatted(payments.get(0).getId()));
	    }

	    @Test
	    void 주문생성_cartItemIds에_중복값이있으면_한번만_주문대상으로처리한다() throws Exception {
	        // given
	        User user = 회원_저장(0L);
	        Product product = 상품_저장("중복 선택 상품", 12000, 10, ProductStatus.ON_SALE);
	        CartItem cartItem = 장바구니상품_저장(user, product, 2);

	        // when
	        // then
	        mockMvc.perform(post("/api/orders")
	                        .header("Authorization", "Bearer " + accessToken(user))
	                        .contentType(MediaType.APPLICATION_JSON)
	                        .content("""
	                                {
	                                  "cartItemIds": [%d, %d],
	                                  "usedPointAmount": 0
	                                }
	                                """.formatted(cartItem.getId(), cartItem.getId())))
	                .andExpect(status().isCreated())
	                .andExpect(jsonPath("$.data.order.totalAmount").value(24000))
	                .andExpect(jsonPath("$.data.order.items.length()").value(1))
	                .andExpect(jsonPath("$.data.order.items[0].orderItemId").isNumber())
	                .andExpect(jsonPath("$.data.order.items[0].productId").value(product.getId()))
	                .andExpect(jsonPath("$.data.order.items[0].quantity").value(2))
	                .andExpect(jsonPath("$.data.order.items[0].unitPrice").value(12000))
	                .andExpect(jsonPath("$.data.order.items[0].lineAmount").value(24000));

	        assertThat(orderRepository.count()).isEqualTo(1);
	        assertThat(orderItemRepository.count()).isEqualTo(1);
	        assertThat(paymentRepository.count()).isEqualTo(1);
	        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(8);
	    }

    @Test
    void 주문생성_재고가부족하면_ORDER_STOCK_SHORTAGE를반환하고_저장하지않는다() throws Exception {
        // given
        User user = 회원_저장(0L);
        Product product = 상품_저장("재고 부족 상품", 10000, 1, ProductStatus.ON_SALE);
        CartItem cartItem = 장바구니상품_저장(user, product, 2);

        // when
        // then
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
	                                {
	                                  "cartItemIds": [%d],
	                                  "usedPointAmount": 0
	                                }
	                                """.formatted(cartItem.getId())))
                .andExpect(status().is(ErrorCode.ORDER_STOCK_SHORTAGE.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.ORDER_STOCK_SHORTAGE.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ORDER_STOCK_SHORTAGE.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
        assertThat(paymentRepository.count()).isZero();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(1);
    }

    @Test
    void 주문생성_포인트잔액이부족하면_INSUFFICIENT_POINT를반환하고_재고를차감하지않는다() throws Exception {
        // given
        User user = 회원_저장(1000L);
        Product product = 상품_저장("맨투맨", 30000, 10, ProductStatus.ON_SALE);
        CartItem cartItem = 장바구니상품_저장(user, product, 1);

        // when
        // then
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
	                                {
	                                  "cartItemIds": [%d],
	                                  "usedPointAmount": 5000
	                                }
	                                """.formatted(cartItem.getId())))
                .andExpect(status().is(ErrorCode.INSUFFICIENT_POINT.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.INSUFFICIENT_POINT.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.INSUFFICIENT_POINT.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
        assertThat(paymentRepository.count()).isZero();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isEqualTo(1000L);
        assertThat(pointTransactionRepository.count()).isZero();
    }

    @Test
    void 주문생성_사용포인트가주문금액보다크면_INVALID_USED_POINT를반환하고_예약하지않는다() throws Exception {
        // given
        User user = 회원_저장(100000L);
        Product product = 상품_저장("포인트 초과 상품", 10000, 10, ProductStatus.ON_SALE);
        CartItem cartItem = 장바구니상품_저장(user, product, 1);

        // when
        // then
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
	                                {
	                                  "cartItemIds": [%d],
	                                  "usedPointAmount": 15000
	                                }
	                                """.formatted(cartItem.getId())))
                .andExpect(status().is(ErrorCode.INVALID_USED_POINT.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_USED_POINT.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_USED_POINT.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
        assertThat(paymentRepository.count()).isZero();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isEqualTo(100000L);
        assertThat(pointTransactionRepository.count()).isZero();
    }

    @Test
    void 주문생성_요청한장바구니상품일부가없으면_CART_ITEM_NOT_FOUND를반환한다() throws Exception {
        // given
        User user = 회원_저장(0L);
        Product product = 상품_저장("가디건", 59000, 10, ProductStatus.ON_SALE);
        CartItem cartItem = 장바구니상품_저장(user, product, 1);
        long unknownCartItemId = 999999L;

        // when
        // then
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
	                                {
	                                  "cartItemIds": [%d, %d],
	                                  "usedPointAmount": 0
	                                }
	                                """.formatted(cartItem.getId(), unknownCartItemId)))
                .andExpect(status().is(ErrorCode.CART_ITEM_NOT_FOUND.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.CART_ITEM_NOT_FOUND.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.CART_ITEM_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
        assertThat(paymentRepository.count()).isZero();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(10);
    }

    @Test
    void 주문생성_판매중이아닌상품이면_PRODUCT_NOT_ON_SALE을반환한다() throws Exception {
        // given
        User user = 회원_저장(0L);
        Product product = 상품_저장("품절 상품", 10000, 10, ProductStatus.SOLD_OUT);
        CartItem cartItem = 장바구니상품_저장(user, product, 1);

        // when
        // then
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
	                                {
	                                  "cartItemIds": [%d],
	                                  "usedPointAmount": 0
	                                }
	                                """.formatted(cartItem.getId())))
                .andExpect(status().is(ErrorCode.PRODUCT_NOT_ON_SALE.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.PRODUCT_NOT_ON_SALE.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.PRODUCT_NOT_ON_SALE.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
        assertThat(paymentRepository.count()).isZero();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(10);
    }

    @Test
    void 주문생성_토큰이없으면_UNAUTHORIZED를반환한다() throws Exception {
        // given

        // when
        // then
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
	                                {
	                                  "usedPointAmount": 0
	                                }
	                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 주문취소_일부주문상품만취소하면_재고를복구하고_결제금액을재계산하고_부분취소상태가된다() throws Exception {
        // given
        User user = 회원_저장(15000L);
        Product firstProduct = 상품_저장("남길 상품", 30000, 10, ProductStatus.ON_SALE);
        Product cancelProduct = 상품_저장("취소 상품", 20000, 10, ProductStatus.ON_SALE);
        장바구니상품_저장(user, firstProduct, 1);
        장바구니상품_저장(user, cancelProduct, 1);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usedPointAmount": 15000
                                }
                                """))
                .andExpect(status().isCreated());

        Order order = orderRepository.findAll().get(0);
        OrderItem cancelOrderItem = orderItemRepository.findAll().stream()
                .filter(orderItem -> orderItem.getProductId().equals(cancelProduct.getId()))
                .findFirst()
                .orElseThrow();

        // when
        // then
        mockMvc.perform(patch("/api/orders/{orderId}/status", order.getId())
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderItemIds": [%d]
                                }
                                """.formatted(cancelOrderItem.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previousOrderStatus").value(OrderStatus.PAYMENT_PENDING.name()))
                .andExpect(jsonPath("$.data.currentOrderStatus").value(OrderStatus.PARTIAL_CANCELED.name()))
                .andExpect(jsonPath("$.data.canceledAmount").value(20000))
                .andExpect(jsonPath("$.data.remainingTotalAmount").value(30000))
                .andExpect(jsonPath("$.data.restoredPointAmount").value(0))
                .andExpect(jsonPath("$.data.remainingUsedPointAmount").value(15000))
                .andExpect(jsonPath("$.data.remainingPgAmount").value(15000))
                .andExpect(jsonPath("$.data.paymentStatus").value(PaymentStatus.PENDING.name()))
                .andExpect(jsonPath("$.data.restoredStockItems[0].orderItemId").value(cancelOrderItem.getId()))
                .andExpect(jsonPath("$.data.restoredStockItems[0].productId").value(cancelProduct.getId()))
                .andExpect(jsonPath("$.data.restoredStockItems[0].restoreQuantity").value(1));

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment updatedPayment = paymentRepository.findAll().get(0);

        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PARTIAL_CANCELED);
        assertThat(updatedOrder.getTotalAmount()).isEqualTo(30000L);
        assertThat(updatedOrder.getUsedPointAmount()).isEqualTo(15000L);
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(updatedPayment.getTotalAmount()).isEqualTo(30000L);
        assertThat(updatedPayment.getUsedPointAmount()).isEqualTo(15000L);
        assertThat(updatedPayment.getPgAmount()).isEqualTo(15000L);
        assertThat(productRepository.findById(firstProduct.getId()).orElseThrow().getStock()).isEqualTo(9);
        assertThat(productRepository.findById(cancelProduct.getId()).orElseThrow().getStock()).isEqualTo(10);
    }

    @Test
    void 주문취소_주문상품수량중_일부수량만취소할수있다() throws Exception {
        // given
        User user = 회원_저장(0L);
        Product doenjang = 상품_저장("된장", 5000, 10, ProductStatus.ON_SALE);
        Product tofu = 상품_저장("두부", 3000, 10, ProductStatus.ON_SALE);
        장바구니상품_저장(user, doenjang, 1);
        장바구니상품_저장(user, tofu, 2);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usedPointAmount": 0
                                }
                                """))
                .andExpect(status().isCreated());

        Order order = orderRepository.findAll().get(0);
        OrderItem tofuOrderItem = orderItemRepository.findAll().stream()
                .filter(orderItem -> orderItem.getProductId().equals(tofu.getId()))
                .findFirst()
                .orElseThrow();

        // when
        // then
        mockMvc.perform(patch("/api/orders/{orderId}/status", order.getId())
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "orderItemId": %d,
                                      "quantity": 1
                                    }
                                  ]
                                }
                                """.formatted(tofuOrderItem.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previousOrderStatus").value(OrderStatus.PAYMENT_PENDING.name()))
                .andExpect(jsonPath("$.data.currentOrderStatus").value(OrderStatus.PARTIAL_CANCELED.name()))
                .andExpect(jsonPath("$.data.canceledAmount").value(3000))
                .andExpect(jsonPath("$.data.remainingTotalAmount").value(8000))
                .andExpect(jsonPath("$.data.remainingUsedPointAmount").value(0))
                .andExpect(jsonPath("$.data.remainingPgAmount").value(8000))
                .andExpect(jsonPath("$.data.paymentStatus").value(PaymentStatus.PENDING.name()))
                .andExpect(jsonPath("$.data.restoredStockItems[0].orderItemId").value(tofuOrderItem.getId()))
                .andExpect(jsonPath("$.data.restoredStockItems[0].productId").value(tofu.getId()))
                .andExpect(jsonPath("$.data.restoredStockItems[0].restoreQuantity").value(1));

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment updatedPayment = paymentRepository.findAll().get(0);
        OrderItem updatedTofuOrderItem = orderItemRepository.findById(tofuOrderItem.getId()).orElseThrow();

        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PARTIAL_CANCELED);
        assertThat(updatedOrder.getTotalAmount()).isEqualTo(8000L);
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(updatedPayment.getTotalAmount()).isEqualTo(8000L);
        assertThat(updatedPayment.getPgAmount()).isEqualTo(8000L);
        assertThat(updatedTofuOrderItem.getStatus()).isEqualTo(OrderItemStatus.ORDERED);
        assertThat(updatedTofuOrderItem.getQuantity()).isEqualTo(1);
        assertThat(productRepository.findById(doenjang.getId()).orElseThrow().getStock()).isEqualTo(9);
        assertThat(productRepository.findById(tofu.getId()).orElseThrow().getStock()).isEqualTo(9);
    }

    @Test
    void 주문취소_부분취소상태이고_결제대기상태이면_남은상품을_다시취소할수있다() throws Exception {
        // given
        User user = 회원_저장(0L);
        Product firstProduct = 상품_저장("먼저 취소할 상품", 10000, 10, ProductStatus.ON_SALE);
        Product secondProduct = 상품_저장("나중에 취소할 상품", 20000, 10, ProductStatus.ON_SALE);
        장바구니상품_저장(user, firstProduct, 1);
        장바구니상품_저장(user, secondProduct, 1);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usedPointAmount": 0
                                }
                                """))
                .andExpect(status().isCreated());

        Order order = orderRepository.findAll().get(0);
        OrderItem firstCancelOrderItem = orderItemRepository.findAll().stream()
                .filter(orderItem -> orderItem.getProductId().equals(firstProduct.getId()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(patch("/api/orders/{orderId}/status", order.getId())
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderItemIds": [%d]
                                }
                                """.formatted(firstCancelOrderItem.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentOrderStatus").value(OrderStatus.PARTIAL_CANCELED.name()));

        // when
        // then
        mockMvc.perform(patch("/api/orders/{orderId}/status", order.getId())
                        .header("Authorization", "Bearer " + accessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previousOrderStatus").value(OrderStatus.PARTIAL_CANCELED.name()))
                .andExpect(jsonPath("$.data.currentOrderStatus").value(OrderStatus.CANCELED.name()))
                .andExpect(jsonPath("$.data.canceledAmount").value(20000))
                .andExpect(jsonPath("$.data.remainingTotalAmount").value(0))
                .andExpect(jsonPath("$.data.remainingUsedPointAmount").value(0))
                .andExpect(jsonPath("$.data.remainingPgAmount").value(0))
                .andExpect(jsonPath("$.data.paymentStatus").value(PaymentStatus.FAILED.name()));

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment updatedPayment = paymentRepository.findAll().get(0);

        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(productRepository.findById(firstProduct.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(productRepository.findById(secondProduct.getId()).orElseThrow().getStock()).isEqualTo(10);
    }

    @Test
    void 주문취소_결제대기주문을취소하면_주문은취소되고_결제는실패상태가되며_재고가복구된다() throws Exception {
        // given
        User user = 회원_저장(0L);
        Product product = 상품_저장("전체 취소 상품", 12000, 10, ProductStatus.ON_SALE);
        장바구니상품_저장(user, product, 2);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usedPointAmount": 0
                                }
                                """))
                .andExpect(status().isCreated());

        Order order = orderRepository.findAll().get(0);

        // when
        // then
        mockMvc.perform(patch("/api/orders/{orderId}/status", order.getId())
                        .header("Authorization", "Bearer " + accessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previousOrderStatus").value(OrderStatus.PAYMENT_PENDING.name()))
                .andExpect(jsonPath("$.data.currentOrderStatus").value(OrderStatus.CANCELED.name()))
                .andExpect(jsonPath("$.data.canceledAmount").value(24000))
                .andExpect(jsonPath("$.data.remainingTotalAmount").value(0))
                .andExpect(jsonPath("$.data.remainingUsedPointAmount").value(0))
                .andExpect(jsonPath("$.data.remainingPgAmount").value(0))
                .andExpect(jsonPath("$.data.paymentStatus").value(PaymentStatus.FAILED.name()));

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment updatedPayment = paymentRepository.findAll().get(0);

        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(updatedOrder.getTotalAmount()).isEqualTo(24000L);
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(updatedPayment.getTotalAmount()).isEqualTo(24000L);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(10);
    }

    @Test
    void 주문취소_선차감된포인트가줄어들면_회원잔액을복구하고_USE_CANCEL원장을생성한다() throws Exception {
        // given
        User user = 회원_저장(15000L);
        Product product = 상품_저장("포인트 복구 상품", 12000, 10, ProductStatus.ON_SALE);
        장바구니상품_저장(user, product, 1);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usedPointAmount": 10000
                                }
                                """))
                .andExpect(status().isCreated());

        Order order = orderRepository.findAll().get(0);

        // when
        // then
        mockMvc.perform(patch("/api/orders/{orderId}/status", order.getId())
                        .header("Authorization", "Bearer " + accessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentOrderStatus").value(OrderStatus.CANCELED.name()))
                .andExpect(jsonPath("$.data.canceledAmount").value(12000))
                .andExpect(jsonPath("$.data.remainingTotalAmount").value(0))
                .andExpect(jsonPath("$.data.restoredPointAmount").value(10000))
                .andExpect(jsonPath("$.data.remainingUsedPointAmount").value(0))
                .andExpect(jsonPath("$.data.remainingPgAmount").value(0))
                .andExpect(jsonPath("$.data.paymentStatus").value(PaymentStatus.FAILED.name()));

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment updatedPayment = paymentRepository.findAll().get(0);
        List<PointTransaction> pointTransactions = pointTransactionRepository.findAll();
        PointTransaction cancelTransaction = pointTransactions.stream()
                .filter(pointTransaction -> pointTransaction.getType() == PointTransactionType.USE_CANCEL)
                .findFirst()
                .orElseThrow();

        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(updatedOrder.getTotalAmount()).isEqualTo(12000L);
        assertThat(updatedOrder.getUsedPointAmount()).isEqualTo(10000L);
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(updatedPayment.getTotalAmount()).isEqualTo(12000L);
        assertThat(updatedPayment.getUsedPointAmount()).isEqualTo(10000L);
        assertThat(updatedPayment.getPgAmount()).isEqualTo(2000L);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isEqualTo(15000L);
        assertThat(pointTransactions).hasSize(2);
        assertThat(cancelTransaction.getAmount()).isEqualTo(10000L);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(10);
    }

    private CartItem 장바구니상품_저장(User user, Product product, int quantity) {
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow();

        return cartItemRepository.save(new CartItem(cart, product, quantity));
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

    private Product 상품_저장(String name, int price, int stock, ProductStatus status) {
        return productRepository.save(new Product(name, price, stock, "테스트 상품입니다.", status, ProductCategory.TOP));
    }

    private String accessToken(User user) {
        return jwtTokenProvider.createAccessToken(user.getId());
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }
}
