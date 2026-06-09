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
    void 부분환불요청_성공하면_환불상품과outbox를생성하고_수량을예약한다() {
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
        assertThat(refund.getRefundAmount()).isEqualTo(2_973L);
        assertThat(refund.getPointRefundAmount()).isZero();
        assertThat(refund.getPgRefundAmount()).isEqualTo(2_973L);
        assertThat(refundItems).hasSize(1);
        assertThat(refundItems.get(0).getRefundQuantity()).isEqualTo(1);
        assertThat(refundedOrderItem.getRefundReservedQuantity()).isEqualTo(1);
        assertThat(refundedOrderItem.getRefundedQuantity()).isZero();
        assertThat(outbox.getStatus()).isEqualTo(RefundOutboxStatus.PENDING);
    }

    @Test
    void 같은멱등키로_같은환불을요청하면_기존환불을반환하고_중복저장하지않는다() {
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
    void 진행중인환불이있으면_다른멱등키의_새환불요청을차단한다() {
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
    void 부분환불완료_성공하면_환불outbox수량결제포인트를갱신한다() {
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
        assertThat(user.getPointBalance()).isZero();
        assertThat(pointTransactionRepository.findAll())
                .extracting("type")
                .doesNotContain(PointTransactionType.USE_RESTORE);
    }

    @Test
    void 환불웹훅완료_cancellationId로_부분환불을완료한다() {
        // given
        RefundFixture fixture = completedPaymentFixture(1_000L, 9_000L);
        RefundResponse response = refundService.requestPartialRefund(
                fixture.user().getId(),
                fixture.order().getId(),
                uniqueKey("refund-webhook-partial"),
                new PartialRefundRequest(
                        "partial refund",
                        List.of(new RefundItemRequest(fixture.firstItem().getId(), 1))
                )
        );
        Long outboxId = onlyOutbox().getId();
        refundProcessingTxService.start(outboxId);
        refundProcessingTxService.retryAsPgResultUnknown(
                outboxId,
                "cancellation-partial-123",
                "PortOne 취소 결과 미확정 상태: REQUESTED"
        );

        // when
        RefundProcessingTxService.RefundWebhookProcessResult result =
                refundProcessingTxService.completeByPortoneCancellationId(
                        fixture.payment().getPortonePaymentId(),
                        "cancellation-partial-123"
                );

        // then
        Refund refund = refundRepository.findById(response.refundId()).orElseThrow();
        RefundOutbox outbox = refundOutboxRepository.findById(outboxId).orElseThrow();
        OrderItem refundedOrderItem = orderItemRepository.findById(fixture.firstItem().getId()).orElseThrow();
        Payment payment = paymentRepository.findById(fixture.payment().getId()).orElseThrow();

        assertThat(result.payment().getId()).isEqualTo(payment.getId());
        assertThat(result.refund().getId()).isEqualTo(refund.getId());
        assertThat(refund.getPortoneCancellationId()).isEqualTo("cancellation-partial-123");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(outbox.getStatus()).isEqualTo(RefundOutboxStatus.SUCCEEDED);
        assertThat(refundedOrderItem.getRefundReservedQuantity()).isZero();
        assertThat(refundedOrderItem.getRefundedQuantity()).isEqualTo(1);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_REFUNDED);
    }

    @Test
    void 환불웹훅완료_같은cancellationId가다시오면_완료상태로멱등응답한다() {
        // given
        RefundFixture fixture = completedPaymentFixture(1_000L, 9_000L);
        RefundResponse response = refundService.requestPartialRefund(
                fixture.user().getId(),
                fixture.order().getId(),
                uniqueKey("refund-webhook-idempotent"),
                new PartialRefundRequest(
                        "partial refund",
                        List.of(new RefundItemRequest(fixture.firstItem().getId(), 1))
                )
        );
        Long outboxId = onlyOutbox().getId();
        String cancellationId = "cancellation-idempotent-123";
        refundProcessingTxService.start(outboxId);
        refundProcessingTxService.retryAsPgResultUnknown(
                outboxId,
                cancellationId,
                "PortOne 취소 결과 미확정 상태: REQUESTED"
        );

        RefundProcessingTxService.RefundWebhookProcessResult firstResult =
                refundProcessingTxService.completeByPortoneCancellationId(
                        fixture.payment().getPortonePaymentId(),
                        cancellationId
                );
        long pointTransactionCountAfterFirstCompletion = pointTransactionRepository.count();

        // when
        RefundProcessingTxService.RefundWebhookProcessResult secondResult =
                refundProcessingTxService.completeByPortoneCancellationId(
                        fixture.payment().getPortonePaymentId(),
                        cancellationId
                );

        // then
        Refund refund = refundRepository.findById(response.refundId()).orElseThrow();
        RefundOutbox outbox = refundOutboxRepository.findById(outboxId).orElseThrow();
        OrderItem refundedOrderItem = orderItemRepository.findById(fixture.firstItem().getId()).orElseThrow();
        Payment payment = paymentRepository.findById(fixture.payment().getId()).orElseThrow();

        assertThat(firstResult.refund().getId()).isEqualTo(response.refundId());
        assertThat(secondResult.refund().getId()).isEqualTo(response.refundId());
        assertThat(secondResult.payment().getId()).isEqualTo(payment.getId());
        assertThat(refund.getPortoneCancellationId()).isEqualTo(cancellationId);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(outbox.getStatus()).isEqualTo(RefundOutboxStatus.SUCCEEDED);
        assertThat(refundedOrderItem.getRefundReservedQuantity()).isZero();
        assertThat(refundedOrderItem.getRefundedQuantity()).isEqualTo(1);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_REFUNDED);
        assertThat(pointTransactionRepository.count()).isEqualTo(pointTransactionCountAfterFirstCompletion);
    }

    @Test
    void 환불웹훅완료_재시도초과로실패한outbox도_PG미확정환불이면복구해완료한다() {
        // given
        RefundFixture fixture = completedPaymentFixture(1_000L, 9_000L);
        RefundResponse response = refundService.requestPartialRefund(
                fixture.user().getId(),
                fixture.order().getId(),
                uniqueKey("refund-webhook-recover-failed"),
                new PartialRefundRequest(
                        "partial refund",
                        List.of(new RefundItemRequest(fixture.firstItem().getId(), 1))
                )
        );
        Long outboxId = onlyOutbox().getId();
        String cancellationId = "cancellation-recover-failed-123";

        for (int attempt = 0; attempt < 6; attempt++) {
            refundProcessingTxService.start(outboxId);
            refundProcessingTxService.retryAsPgResultUnknown(
                    outboxId,
                    cancellationId,
                    "PortOne 취소 결과 미확정 상태: REQUESTED"
            );
        }

        Refund pendingRefund = refundRepository.findById(response.refundId()).orElseThrow();
        RefundOutbox failedOutbox = refundOutboxRepository.findById(outboxId).orElseThrow();

        assertThat(pendingRefund.getStatus()).isEqualTo(RefundStatus.PG_RESULT_UNKNOWN);
        assertThat(failedOutbox.getStatus()).isEqualTo(RefundOutboxStatus.FAILED);

        // when
        RefundProcessingTxService.RefundWebhookProcessResult result =
                refundProcessingTxService.completeByPortoneCancellationId(
                        fixture.payment().getPortonePaymentId(),
                        cancellationId
                );

        // then
        Refund refund = refundRepository.findById(response.refundId()).orElseThrow();
        RefundOutbox outbox = refundOutboxRepository.findById(outboxId).orElseThrow();
        OrderItem refundedOrderItem = orderItemRepository.findById(fixture.firstItem().getId()).orElseThrow();
        Payment payment = paymentRepository.findById(fixture.payment().getId()).orElseThrow();

        assertThat(result.payment().getId()).isEqualTo(payment.getId());
        assertThat(result.refund().getId()).isEqualTo(refund.getId());
        assertThat(refund.getPortoneCancellationId()).isEqualTo(cancellationId);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(outbox.getStatus()).isEqualTo(RefundOutboxStatus.SUCCEEDED);
        assertThat(outbox.getLastErrorMessage()).isNull();
        assertThat(refundedOrderItem.getRefundReservedQuantity()).isZero();
        assertThat(refundedOrderItem.getRefundedQuantity()).isEqualTo(1);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_REFUNDED);
    }

    @Test
    void 환불웹훅완료_PG호출예외로취소ID가저장되지않아도_미확정환불을완료한다() {
        // given
        RefundFixture fixture = completedPaymentFixture(1_000L, 9_000L);
        RefundResponse response = refundService.requestPartialRefund(
                fixture.user().getId(),
                fixture.order().getId(),
                uniqueKey("refund-webhook-unidentified"),
                new PartialRefundRequest(
                        "partial refund",
                        List.of(new RefundItemRequest(fixture.firstItem().getId(), 1))
                )
        );
        Long outboxId = onlyOutbox().getId();
        String cancellationId = "cancellation-unidentified-123";

        refundProcessingTxService.start(outboxId);
        refundProcessingTxService.retryAsPgResultUnknown(outboxId, "PortOne 취소 API 호출 타임아웃");

        Refund pendingRefund = refundRepository.findById(response.refundId()).orElseThrow();
        RefundOutbox pendingOutbox = refundOutboxRepository.findById(outboxId).orElseThrow();

        assertThat(pendingRefund.getStatus()).isEqualTo(RefundStatus.PG_RESULT_UNKNOWN);
        assertThat(pendingRefund.getPortoneCancellationId()).isNull();
        assertThat(pendingOutbox.getStatus()).isEqualTo(RefundOutboxStatus.PENDING);

        // when
        RefundProcessingTxService.RefundWebhookProcessResult result =
                refundProcessingTxService.completeByPortoneCancellationId(
                        fixture.payment().getPortonePaymentId(),
                        cancellationId
                );

        // then
        Refund refund = refundRepository.findById(response.refundId()).orElseThrow();
        RefundOutbox outbox = refundOutboxRepository.findById(outboxId).orElseThrow();
        OrderItem refundedOrderItem = orderItemRepository.findById(fixture.firstItem().getId()).orElseThrow();
        Payment payment = paymentRepository.findById(fixture.payment().getId()).orElseThrow();

        assertThat(result.payment().getId()).isEqualTo(payment.getId());
        assertThat(result.refund().getId()).isEqualTo(refund.getId());
        assertThat(refund.getPortoneCancellationId()).isEqualTo(cancellationId);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(outbox.getStatus()).isEqualTo(RefundOutboxStatus.SUCCEEDED);
        assertThat(refundedOrderItem.getRefundReservedQuantity()).isZero();
        assertThat(refundedOrderItem.getRefundedQuantity()).isEqualTo(1);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_REFUNDED);
    }

    @Test
    void 환불웹훅완료_완료된취소ID가중복으로오면_새환불을완료하지않는다() {
        // given
        RefundFixture fixture = completedPaymentFixture(1_000L, 9_000L);
        RefundResponse firstResponse = refundService.requestPartialRefund(
                fixture.user().getId(),
                fixture.order().getId(),
                uniqueKey("refund-first-webhook-duplicate"),
                new PartialRefundRequest(
                        "first partial refund",
                        List.of(new RefundItemRequest(fixture.firstItem().getId(), 1))
                )
        );
        Long firstOutboxId = onlyOutbox().getId();
        String firstCancellationId = "cancellation-duplicate-old-123";

        refundProcessingTxService.start(firstOutboxId);
        refundProcessingTxService.retryAsPgResultUnknown(
                firstOutboxId,
                firstCancellationId,
                "PortOne 취소 결과 미확정 상태: REQUESTED"
        );
        refundProcessingTxService.completeByPortoneCancellationId(
                fixture.payment().getPortonePaymentId(),
                firstCancellationId
        );

        RefundResponse secondResponse = refundService.requestPartialRefund(
                fixture.user().getId(),
                fixture.order().getId(),
                uniqueKey("refund-second-active"),
                new PartialRefundRequest(
                        "second partial refund",
                        List.of(new RefundItemRequest(fixture.secondItem().getId(), 1))
                )
        );
        RefundOutbox secondOutboxBeforeWebhook = latestOutbox();

        // when
        RefundProcessingTxService.RefundWebhookProcessResult duplicateResult =
                refundProcessingTxService.completeByPortoneCancellationId(
                        fixture.payment().getPortonePaymentId(),
                        firstCancellationId
                );

        // then
        Refund firstRefund = refundRepository.findById(firstResponse.refundId()).orElseThrow();
        Refund secondRefund = refundRepository.findById(secondResponse.refundId()).orElseThrow();
        RefundOutbox secondOutbox = refundOutboxRepository.findById(secondOutboxBeforeWebhook.getId()).orElseThrow();

        assertThat(duplicateResult.refund().getId()).isEqualTo(firstRefund.getId());
        assertThat(firstRefund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(secondRefund.getStatus()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(secondRefund.getPortoneCancellationId()).isNull();
        assertThat(secondOutbox.getStatus()).isEqualTo(RefundOutboxStatus.PENDING);
    }

    @Test
    void 전체환불완료_성공하면_결제를전체환불로_주문을취소로변경한다() {
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
    void 환불웹훅완료_cancellationId로_전체환불을완료한다() {
        // given
        RefundFixture fixture = completedPaymentFixture(1_000L, 9_000L);
        RefundResponse response = refundService.requestFullRefund(
                fixture.user().getId(),
                fixture.payment().getId(),
                uniqueKey("refund-webhook-full"),
                new FullRefundRequest("full refund")
        );
        Long outboxId = onlyOutbox().getId();
        refundProcessingTxService.start(outboxId);
        refundProcessingTxService.retryAsPgResultUnknown(
                outboxId,
                "cancellation-full-123",
                "PortOne 취소 결과 미확정 상태: REQUESTED"
        );

        // when
        RefundProcessingTxService.RefundWebhookProcessResult result =
                refundProcessingTxService.completeByPortoneCancellationId(
                        fixture.payment().getPortonePaymentId(),
                        "cancellation-full-123"
                );

        // then
        Refund refund = refundRepository.findById(response.refundId()).orElseThrow();
        RefundOutbox outbox = refundOutboxRepository.findById(outboxId).orElseThrow();
        Payment payment = paymentRepository.findById(fixture.payment().getId()).orElseThrow();
        Order order = orderRepository.findById(fixture.order().getId()).orElseThrow();

        assertThat(result.payment().getId()).isEqualTo(payment.getId());
        assertThat(result.refund().getId()).isEqualTo(refund.getId());
        assertThat(refund.getPortoneCancellationId()).isEqualTo("cancellation-full-123");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(outbox.getStatus()).isEqualTo(RefundOutboxStatus.SUCCEEDED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FULL_REFUNDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void 환불실패_확정되면_예약수량을해제하고_결제상태는유지한다() {
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
    void 환불실패_확정되면_예약한적립포인트회수금액을_회원잔액으로되돌린다() {
        // given
        RefundFixture fixture = completedPaymentFixture(0L, 9_000L);
        fixture.user().increasePointBalance(100L);
        userRepository.saveAndFlush(fixture.user());

        RefundResponse response = refundService.requestPartialRefund(
                fixture.user().getId(),
                fixture.order().getId(),
                uniqueKey("refund-fail-point-release"),
                new PartialRefundRequest(
                        "partial refund",
                        List.of(new RefundItemRequest(fixture.firstItem().getId(), 1))
                )
        );
        Long outboxId = onlyOutbox().getId();
        User userAfterReserve = userRepository.findById(fixture.user().getId()).orElseThrow();

        refundProcessingTxService.start(outboxId);

        // when
        refundProcessingTxService.fail(outboxId, "PortOne cancel failed");

        // then
        Refund refund = refundRepository.findById(response.refundId()).orElseThrow();
        User userAfterFail = userRepository.findById(fixture.user().getId()).orElseThrow();

        assertThat(refund.getRecoveredFromBalance()).isEqualTo(30L);
        assertThat(userAfterReserve.getPointBalance()).isEqualTo(70L);
        assertThat(userAfterFail.getPointBalance()).isEqualTo(100L);
        assertThat(pointTransactionRepository.findAll())
                .extracting("type", "amount")
                .contains(
                        org.assertj.core.groups.Tuple.tuple(PointTransactionType.EARN_RECOVERY_RESERVE, 30L),
                        org.assertj.core.groups.Tuple.tuple(PointTransactionType.EARN_RECOVERY_RELEASE, 30L)
                );
    }

    @Test
    void PG결과미확정이면_예약수량을유지하고_재시도를예약한다() {
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
    void PG결과미확정_재시도초과해도_환불상태와예약수량을유지하고_새환불을차단한다() {
        // given
        RefundFixture fixture = completedPaymentFixture(1_000L, 9_000L);
        RefundResponse response = refundService.requestPartialRefund(
                fixture.user().getId(),
                fixture.order().getId(),
                uniqueKey("refund-unknown-retry-exceeded"),
                new PartialRefundRequest(
                        "부분 환불",
                        List.of(new RefundItemRequest(fixture.firstItem().getId(), 1))
                )
        );
        Long outboxId = onlyOutbox().getId();

        // when
        for (int attempt = 0; attempt < 6; attempt++) {
            refundProcessingTxService.start(outboxId);
            refundProcessingTxService.retryAsPgResultUnknown(outboxId, "PortOne 취소 API 호출 타임아웃");
        }

        // then
        Refund refund = refundRepository.findById(response.refundId()).orElseThrow();
        RefundOutbox outbox = refundOutboxRepository.findById(outboxId).orElseThrow();
        OrderItem refundedOrderItem = orderItemRepository.findById(fixture.firstItem().getId()).orElseThrow();
        Payment payment = paymentRepository.findById(fixture.payment().getId()).orElseThrow();

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PG_RESULT_UNKNOWN);
        assertThat(refund.getFailedReason()).isNull();
        assertThat(outbox.getStatus()).isEqualTo(RefundOutboxStatus.FAILED);
        assertThat(outbox.getRetryCount()).isEqualTo(6);
        assertThat(refundedOrderItem.getRefundReservedQuantity()).isEqualTo(1);
        assertThat(refundedOrderItem.getRefundedQuantity()).isZero();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        assertThatThrownBy(() -> refundService.requestPartialRefund(
                fixture.user().getId(),
                fixture.order().getId(),
                uniqueKey("refund-after-retry-exceeded"),
                new PartialRefundRequest(
                        "두 번째 환불",
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
    void 마지막전체환불은_이전부분환불후_남은포인트와PG금액을모두배분한다() {
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

        assertThat(refund.getRefundAmount()).isEqualTo(6_937L);
        assertThat(refund.getPointRefundAmount()).isEqualTo(937L);
        assertThat(refund.getPgRefundAmount()).isEqualTo(6_000L);
        assertThat(refund.getRefundAmount())
                .isEqualTo(refund.getPointRefundAmount() + refund.getPgRefundAmount());
        assertThat(refundItems).hasSize(2);
        assertThat(refundItems)
                .extracting(RefundItem::getRefundAmount)
                .containsExactlyInAnyOrder(3_000L, 4_000L);
        // 상품별 실제 반환액(point + pg)은 상품 기준 환불 금액을 넘으면 안 됩니다.
        // 마지막 환불에서 버림 오차를 보정하더라도 RefundItem 검증 조건을 지켜야 합니다.
        assertThat(refundItems)
                .allSatisfy(refundItem ->
                        assertThat(refundItem.getPointRefundAmount() + refundItem.getPgRefundAmount())
                                .isLessThanOrEqualTo(refundItem.getRefundAmount())
                );
        assertThat(refundItems.stream().mapToLong(RefundItem::getPointRefundAmount).sum())
                .isEqualTo(refund.getPointRefundAmount());
        assertThat(refundItems.stream().mapToLong(RefundItem::getPgRefundAmount).sum())
                .isEqualTo(refund.getPgRefundAmount());
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

    private RefundOutbox latestOutbox() {
        return refundOutboxRepository.findAll().stream()
                .max(java.util.Comparator.comparing(RefundOutbox::getId))
                .orElseThrow();
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
