package com.teamec2.paymentsystem.domain.refund.service;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.order.entity.OrderStatus;
import com.teamec2.paymentsystem.domain.order.repository.OrderItemRepository;
import com.teamec2.paymentsystem.domain.order.repository.OrderRepository;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentStatus;
import com.teamec2.paymentsystem.domain.payment.repository.PaymentRepository;
import com.teamec2.paymentsystem.domain.point.enums.PointTransactionType;
import com.teamec2.paymentsystem.domain.point.repository.PointTransactionRepository;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductCategory;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;
import com.teamec2.paymentsystem.domain.product.repository.ProductRepository;
import com.teamec2.paymentsystem.domain.refund.dto.FullRefundRequest;
import com.teamec2.paymentsystem.domain.refund.dto.PartialRefundRequest;
import com.teamec2.paymentsystem.domain.refund.dto.RefundItemRequest;
import com.teamec2.paymentsystem.domain.refund.dto.RefundResponse;
import com.teamec2.paymentsystem.domain.refund.entity.Refund;
import com.teamec2.paymentsystem.domain.refund.entity.RefundItem;
import com.teamec2.paymentsystem.domain.refund.entity.RefundOutbox;
import com.teamec2.paymentsystem.domain.refund.enums.RefundOutboxStatus;
import com.teamec2.paymentsystem.domain.refund.enums.RefundStatus;
import com.teamec2.paymentsystem.domain.refund.repository.RefundItemRepository;
import com.teamec2.paymentsystem.domain.refund.repository.RefundOutboxRepository;
import com.teamec2.paymentsystem.domain.refund.repository.RefundRepository;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.domain.user.repository.UserRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RefundServiceIntegrationTest {

    @Autowired
    RefundService refundService;

    @Autowired
    RefundProcessingTxService refundProcessingTxService;

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
    RefundRepository refundRepository;

    @Autowired
    RefundItemRepository refundItemRepository;

    @Autowired
    RefundOutboxRepository refundOutboxRepository;

    @Autowired
    PointTransactionRepository pointTransactionRepository;

    @BeforeEach
    void setUp() {
        clearDatabase();
    }

    @AfterEach
    void tearDown() {
        clearDatabase();
    }

    @Test
    void request_partial_refund_creates_refund_items_outbox_and_reserves_quantity() {
        // given
        RefundFixture fixture = completedPaymentFixture(1_000L, 9_000L);
        String idempotencyKey = uniqueKey("refund-partial");

        // when
        RefundResponse response = refundService.requestPartialRefund(
                fixture.user().getId(),
                fixture.order().getId(),
                idempotencyKey,
                new PartialRefundRequest(
                        "partial refund",
                        List.of(new RefundItemRequest(fixture.firstItem().getId(), 1))
                )
        );

        // then
        Refund refund = refundRepository.findById(response.refundId()).orElseThrow();
        List<RefundItem> refundItems = refundItemRepository.findAllByRefund_Id(refund.getId());
        OrderItem refundedOrderItem = orderItemRepository.findById(fixture.firstItem().getId()).orElseThrow();
        RefundOutbox outbox = onlyOutbox();

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(refund.getRefundAmount()).isEqualTo(3_000L);
        assertThat(refund.getPointRefundAmount()).isEqualTo(300L);
        assertThat(refund.getPgRefundAmount()).isEqualTo(2_700L);
        assertThat(refundItems).hasSize(1);
        assertThat(refundItems.get(0).getRefundQuantity()).isEqualTo(1);
        assertThat(refundedOrderItem.getRefundReservedQuantity()).isEqualTo(1);
        assertThat(refundedOrderItem.getRefundedQuantity()).isZero();
        assertThat(outbox.getStatus()).isEqualTo(RefundOutboxStatus.PENDING);
    }

    @Test
    void same_idempotency_key_returns_existing_refund_without_duplicate_rows() {
        // given
        RefundFixture fixture = completedPaymentFixture(1_000L, 9_000L);
        String idempotencyKey = uniqueKey("refund-idempotent");
        PartialRefundRequest request = new PartialRefundRequest(
                "partial refund",
                List.of(new RefundItemRequest(fixture.firstItem().getId(), 1))
        );

        RefundResponse firstResponse = refundService.requestPartialRefund(
                fixture.user().getId(),
                fixture.order().getId(),
                idempotencyKey,
                request
        );

        // when
        RefundResponse secondResponse = refundService.requestPartialRefund(
                fixture.user().getId(),
                fixture.order().getId(),
                idempotencyKey,
                request
        );

        // then
        OrderItem refundedOrderItem = orderItemRepository.findById(fixture.firstItem().getId()).orElseThrow();

        assertThat(secondResponse.refundId()).isEqualTo(firstResponse.refundId());
        assertThat(refundRepository.count()).isEqualTo(1);
        assertThat(refundItemRepository.count()).isEqualTo(1);
        assertThat(refundOutboxRepository.count()).isEqualTo(1);
        assertThat(refundedOrderItem.getRefundReservedQuantity()).isEqualTo(1);
    }

    @Test
    void active_refund_blocks_new_refund_with_different_idempotency_key() {
        // given
        RefundFixture fixture = completedPaymentFixture(1_000L, 9_000L);
        refundService.requestPartialRefund(
                fixture.user().getId(),
                fixture.order().getId(),
                uniqueKey("refund-processing"),
                new PartialRefundRequest(
                        "first refund",
                        List.of(new RefundItemRequest(fixture.firstItem().getId(), 1))
                )
        );

        // when & then
        assertThatThrownBy(() -> refundService.requestPartialRefund(
                fixture.user().getId(),
                fixture.order().getId(),
                uniqueKey("refund-other"),
                new PartialRefundRequest(
                        "second refund",
                        List.of(new RefundItemRequest(fixture.secondItem().getId(), 1))
                )
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFUND_IN_PROGRESS);

        assertThat(refundRepository.count()).isEqualTo(1);
        assertThat(refundItemRepository.count()).isEqualTo(1);
        assertThat(refundOutboxRepository.count()).isEqualTo(1);
    }

    @Test
    void complete_partial_refund_updates_refund_outbox_quantity_payment_and_points() {
        // given
        RefundFixture fixture = completedPaymentFixture(1_000L, 9_000L);
        RefundResponse response = refundService.requestPartialRefund(
                fixture.user().getId(),
                fixture.order().getId(),
                uniqueKey("refund-complete-partial"),
                new PartialRefundRequest(
                        "partial refund",
                        List.of(new RefundItemRequest(fixture.firstItem().getId(), 1))
                )
        );
        Long outboxId = onlyOutbox().getId();
        refundProcessingTxService.start(outboxId);

        // when
        refundProcessingTxService.complete(outboxId);

        // then
        Refund refund = refundRepository.findById(response.refundId()).orElseThrow();
        RefundOutbox outbox = refundOutboxRepository.findById(outboxId).orElseThrow();
        OrderItem refundedOrderItem = orderItemRepository.findById(fixture.firstItem().getId()).orElseThrow();
        Payment payment = paymentRepository.findById(fixture.payment().getId()).orElseThrow();
        User user = userRepository.findById(fixture.user().getId()).orElseThrow();

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(outbox.getStatus()).isEqualTo(RefundOutboxStatus.SUCCEEDED);
        assertThat(refundedOrderItem.getRefundReservedQuantity()).isZero();
        assertThat(refundedOrderItem.getRefundedQuantity()).isEqualTo(1);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_REFUNDED);
        assertThat(user.getPointBalance()).isEqualTo(300L);
        assertThat(pointTransactionRepository.findAll())
                .extracting("type")
                .contains(PointTransactionType.USE_RESTORE);
    }

    @Test
    void complete_full_refund_updates_payment_to_full_refunded_and_order_to_canceled() {
        // given
        RefundFixture fixture = completedPaymentFixture(1_000L, 9_000L);
        RefundResponse response = refundService.requestFullRefund(
                fixture.user().getId(),
                fixture.payment().getId(),
                uniqueKey("refund-complete-full"),
                new FullRefundRequest("full refund")
        );
        Long outboxId = onlyOutbox().getId();
        refundProcessingTxService.start(outboxId);

        // when
        refundProcessingTxService.complete(outboxId);

        // then
        Refund refund = refundRepository.findById(response.refundId()).orElseThrow();
        RefundOutbox outbox = refundOutboxRepository.findById(outboxId).orElseThrow();
        Payment payment = paymentRepository.findById(fixture.payment().getId()).orElseThrow();
        Order order = orderRepository.findById(fixture.order().getId()).orElseThrow();

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(outbox.getStatus()).isEqualTo(RefundOutboxStatus.SUCCEEDED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FULL_REFUNDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(orderItemRepository.findAll())
                .allMatch(orderItem -> orderItem.getRefundReservedQuantity() == 0)
                .allMatch(orderItem -> orderItem.getRemainingRefundableQuantity() == 0);
    }

    @Test
    void fail_refund_releases_reserved_quantity_and_keeps_payment_status() {
        // given
        RefundFixture fixture = completedPaymentFixture(1_000L, 9_000L);
        RefundResponse response = refundService.requestPartialRefund(
                fixture.user().getId(),
                fixture.order().getId(),
                uniqueKey("refund-fail"),
                new PartialRefundRequest(
                        "partial refund",
                        List.of(new RefundItemRequest(fixture.firstItem().getId(), 1))
                )
        );
        Long outboxId = onlyOutbox().getId();
        refundProcessingTxService.start(outboxId);

        // when
        refundProcessingTxService.fail(outboxId, "PortOne cancel failed");

        // then
        Refund refund = refundRepository.findById(response.refundId()).orElseThrow();
        RefundOutbox outbox = refundOutboxRepository.findById(outboxId).orElseThrow();
        OrderItem refundedOrderItem = orderItemRepository.findById(fixture.firstItem().getId()).orElseThrow();
        Payment payment = paymentRepository.findById(fixture.payment().getId()).orElseThrow();

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(outbox.getStatus()).isEqualTo(RefundOutboxStatus.FAILED);
        assertThat(refundedOrderItem.getRefundReservedQuantity()).isZero();
        assertThat(refundedOrderItem.getRefundedQuantity()).isZero();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void unknown_pg_result_keeps_reserved_quantity_and_schedules_retry() {
        // given
        RefundFixture fixture = completedPaymentFixture(1_000L, 9_000L);
        RefundResponse response = refundService.requestPartialRefund(
                fixture.user().getId(),
                fixture.order().getId(),
                uniqueKey("refund-unknown"),
                new PartialRefundRequest(
                        "partial refund",
                        List.of(new RefundItemRequest(fixture.firstItem().getId(), 1))
                )
        );
        Long outboxId = onlyOutbox().getId();
        refundProcessingTxService.start(outboxId);
        LocalDateTime beforeRetry = LocalDateTime.now();

        // when
        refundProcessingTxService.retryAsPgResultUnknown(outboxId, "timeout");

        // then
        Refund refund = refundRepository.findById(response.refundId()).orElseThrow();
        RefundOutbox outbox = refundOutboxRepository.findById(outboxId).orElseThrow();
        OrderItem refundedOrderItem = orderItemRepository.findById(fixture.firstItem().getId()).orElseThrow();

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PG_RESULT_UNKNOWN);
        assertThat(outbox.getStatus()).isEqualTo(RefundOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isAfter(beforeRetry);
        assertThat(refundedOrderItem.getRefundReservedQuantity()).isEqualTo(1);
        assertThat(refundedOrderItem.getRefundedQuantity()).isZero();
    }

    @Test
    void final_refund_allocates_all_remaining_point_and_pg_amount_after_completed_partial_refund() {
        // given
        RefundFixture fixture = completedPaymentFixture(1_000L, 9_000L);
        refundService.requestPartialRefund(
                fixture.user().getId(),
                fixture.order().getId(),
                uniqueKey("refund-first-partial"),
                new PartialRefundRequest(
                        "first partial refund",
                        List.of(new RefundItemRequest(fixture.firstItem().getId(), 1))
                )
        );
        Long firstOutboxId = onlyOutbox().getId();
        refundProcessingTxService.start(firstOutboxId);
        refundProcessingTxService.complete(firstOutboxId);

        // when
        RefundResponse response = refundService.requestFullRefund(
                fixture.user().getId(),
                fixture.payment().getId(),
                uniqueKey("refund-final"),
                new FullRefundRequest("final refund")
        );

        // then
        Refund refund = refundRepository.findById(response.refundId()).orElseThrow();
        List<RefundItem> refundItems = refundItemRepository.findAllByRefund_Id(refund.getId());

        assertThat(refund.getRefundAmount()).isEqualTo(7_000L);
        assertThat(refund.getPointRefundAmount()).isEqualTo(700L);
        assertThat(refund.getPgRefundAmount()).isEqualTo(6_300L);
        assertThat(refund.getRefundAmount())
                .isEqualTo(refund.getPointRefundAmount() + refund.getPgRefundAmount());
        assertThat(refundItems).hasSize(2);
        assertThat(refundItems)
                .extracting(RefundItem::getRefundAmount)
                .containsExactlyInAnyOrder(3_000L, 4_000L);
        assertThat(refundOutboxRepository.count()).isEqualTo(2);
    }

    private RefundFixture completedPaymentFixture(Long usedPointAmount, Long pgAmount) {
        User user = userRepository.save(User.create(uniqueEmail(), "Password123!", "test-user", "010-1234-5678"));
        Order order = orderRepository.save(Order.create(
                user,
                "ORDER-" + UUID.randomUUID(),
                usedPointAmount + pgAmount,
                usedPointAmount
        ));
        Product firstProduct = productRepository.save(product("first-product", 3_000, 10));
        Product secondProduct = productRepository.save(product("second-product", 4_000, 10));
        OrderItem firstItem = orderItemRepository.save(new OrderItem(order, firstProduct, 1L, 2));
        OrderItem secondItem = orderItemRepository.save(new OrderItem(order, secondProduct, 2L, 1));
        Payment payment = paymentRepository.save(Payment.createPending(
                order,
                usedPointAmount + pgAmount,
                usedPointAmount,
                pgAmount,
                pgAmount / 100
        ));

        order.complete();
        payment.complete(LocalDateTime.of(2026, 6, 1, 12, 30));
        orderRepository.saveAndFlush(order);
        paymentRepository.saveAndFlush(payment);

        return new RefundFixture(user, order, firstItem, secondItem, payment);
    }

    private Product product(String name, int price, int stock) {
        return new Product(
                name,
                price,
                stock,
                "test product",
                ProductStatus.ON_SALE,
                ProductCategory.TOP
        );
    }

    private RefundOutbox onlyOutbox() {
        assertThat(refundOutboxRepository.count()).isEqualTo(1);
        return refundOutboxRepository.findAll().get(0);
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    private String uniqueKey(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private void clearDatabase() {
        refundItemRepository.deleteAll();
        refundOutboxRepository.deleteAll();
        pointTransactionRepository.deleteAll();
        refundRepository.deleteAll();
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    private record RefundFixture(
            User user,
            Order order,
            OrderItem firstItem,
            OrderItem secondItem,
            Payment payment
    ) {
    }
}
