package com.teamec2.paymentsystem.domain.refund.service;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.order.repository.OrderItemRepository;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.point.service.PointService;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductCategory;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;
import com.teamec2.paymentsystem.domain.product.repository.ProductRepository;
import com.teamec2.paymentsystem.domain.refund.entity.Refund;
import com.teamec2.paymentsystem.domain.refund.entity.RefundItem;
import com.teamec2.paymentsystem.domain.refund.entity.RefundOutbox;
import com.teamec2.paymentsystem.domain.refund.enums.RefundOutboxStatus;
import com.teamec2.paymentsystem.domain.refund.enums.RefundStatus;
import com.teamec2.paymentsystem.domain.refund.repository.RefundItemRepository;
import com.teamec2.paymentsystem.domain.refund.repository.RefundOutboxRepository;
import com.teamec2.paymentsystem.domain.refund.repository.RefundRepository;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundProcessingTxServiceTest {

    @Mock
    RefundOutboxRepository refundOutboxRepository;

    @Mock
    RefundRepository refundRepository;

    @Mock
    RefundItemRepository refundItemRepository;

    @Mock
    OrderItemRepository orderItemRepository;

    @Mock
    PointService pointService;

    @Mock
    ProductRepository productRepository;

    RefundProcessingTxService refundProcessingTxService;

    @BeforeEach
    void setUp() {
        refundProcessingTxService = new RefundProcessingTxService(
                refundOutboxRepository,
                refundRepository,
                refundItemRepository,
                orderItemRepository,
                pointService,
                productRepository
        );
    }

    @Test
    void 환불시작_대기상태가아니면_OptionalEmpty를반환한다() {
        // given
        RefundOutbox outbox = 환불Outbox(1L, 환불(결제(1L, 10L)));
        outbox.markProcessing(LocalDateTime.now());
        when(refundOutboxRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(outbox));

        // when
        Optional<RefundCancelCommand> command = refundProcessingTxService.start(1L);

        // then
        assertThat(command).isEmpty();
        verify(refundRepository, never()).sumCompletedPgRefundAmount(10L);
    }

    @Test
    void 환불시작_환불상태가처리대상이아니면_outbox를실패처리하고_OptionalEmpty를반환한다() {
        // given
        Refund refund = 환불(결제(1L, 10L));
        refund.fail("이미 실패한 환불");
        RefundOutbox outbox = 환불Outbox(1L, refund);
        when(refundOutboxRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(outbox));

        // when
        Optional<RefundCancelCommand> command = refundProcessingTxService.start(1L);

        // then
        assertThat(command).isEmpty();
        assertThat(outbox.getStatus()).isEqualTo(RefundOutboxStatus.FAILED);
        assertThat(outbox.getLastErrorMessage()).isEqualTo("처리 가능한 환불 상태가 아닙니다.");
    }

    @Test
    void 환불시작_PG취소가능금액보다요청금액이크면_실패처리하고_OptionalEmpty를반환한다() {
        // given
        Payment payment = 결제(1L, 10L);
        Refund refund = 환불(payment);
        RefundOutbox outbox = 환불Outbox(1L, refund);
        when(refundOutboxRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(outbox));
        when(refundRepository.sumCompletedPgRefundAmount(10L)).thenReturn(300L);
        when(refundItemRepository.findAllByRefundIdWithOrderItem(refund.getId())).thenReturn(List.of());

        // when
        Optional<RefundCancelCommand> command = refundProcessingTxService.start(1L);

        // then
        assertThat(command).isEmpty();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(outbox.getStatus()).isEqualTo(RefundOutboxStatus.FAILED);
        verify(pointService).releaseReservedEarnedPointRecovery(payment, refund);
    }

    @Test
    void 환불웹훅완료_처리대상과완료대상과미식별후보가없으면_REFUND_NOT_ALLOWED가발생한다() {
        // given
        when(refundOutboxRepository.findProcessableByPortoneCancellationIdForUpdate("pay_123", "cancel_123"))
                .thenReturn(Optional.empty());
        when(refundOutboxRepository.findRecoverableFailedByPortoneCancellationIdForUpdate("pay_123", "cancel_123"))
                .thenReturn(Optional.empty());
        when(refundRepository.findByPortonePaymentIdAndPortoneCancellationId("pay_123", "cancel_123"))
                .thenReturn(Optional.empty());
        when(refundOutboxRepository.findUnidentifiedWebhookCandidatesForUpdate("pay_123"))
                .thenReturn(List.of());

        // when
        // then
        assertThatThrownBy(() -> refundProcessingTxService.completeByPortoneCancellationId("pay_123", "cancel_123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFUND_NOT_ALLOWED);
    }

    @Test
    void 환불웹훅완료_미식별후보가여러개면_REFUND_NOT_ALLOWED가발생한다() {
        // given
        RefundOutbox firstOutbox = 환불Outbox(1L, 환불(결제(1L, 10L)));
        RefundOutbox secondOutbox = 환불Outbox(2L, 환불(결제(2L, 20L)));

        when(refundOutboxRepository.findProcessableByPortoneCancellationIdForUpdate("pay_123", "cancel_123"))
                .thenReturn(Optional.empty());
        when(refundOutboxRepository.findRecoverableFailedByPortoneCancellationIdForUpdate("pay_123", "cancel_123"))
                .thenReturn(Optional.empty());
        when(refundRepository.findByPortonePaymentIdAndPortoneCancellationId("pay_123", "cancel_123"))
                .thenReturn(Optional.empty());
        when(refundOutboxRepository.findUnidentifiedWebhookCandidatesForUpdate("pay_123"))
                .thenReturn(List.of(firstOutbox, secondOutbox));

        // when
        // then
        assertThatThrownBy(() -> refundProcessingTxService.completeByPortoneCancellationId("pay_123", "cancel_123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFUND_NOT_ALLOWED);
    }

    @Test
    void 환불웹훅완료_FAILED미식별후보가_PG미확정이아니면_REFUND_NOT_ALLOWED가발생한다() {
        // given
        Refund refund = 환불(결제(1L, 10L));
        refund.fail("실패 확정");
        RefundOutbox failedOutbox = 환불Outbox(1L, refund);
        failedOutbox.markFailed("실패 확정");

        when(refundOutboxRepository.findProcessableByPortoneCancellationIdForUpdate("pay_123", "cancel_123"))
                .thenReturn(Optional.empty());
        when(refundOutboxRepository.findRecoverableFailedByPortoneCancellationIdForUpdate("pay_123", "cancel_123"))
                .thenReturn(Optional.empty());
        when(refundRepository.findByPortonePaymentIdAndPortoneCancellationId("pay_123", "cancel_123"))
                .thenReturn(Optional.empty());
        when(refundOutboxRepository.findUnidentifiedWebhookCandidatesForUpdate("pay_123"))
                .thenReturn(List.of(failedOutbox));

        // when
        // then
        assertThatThrownBy(() -> refundProcessingTxService.completeByPortoneCancellationId("pay_123", "cancel_123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFUND_NOT_ALLOWED);
    }

    @Test
    void 환불완료_잠근상품조회결과가부족하면_PRODUCT_NOT_FOUND가발생한다() {
        // given
        Payment payment = 결제(1L, 10L);
        Refund refund = 환불(payment);
        RefundOutbox outbox = 환불Outbox(1L, refund);
        OrderItem orderItem = 주문상품(payment.getOrder(), 100L, 3_000, 1);
        RefundItem refundItem = RefundItem.createRefundItem(refund, orderItem, 1, 200L, 800L);

        outbox.markProcessing(LocalDateTime.now());

        when(refundOutboxRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(outbox));
        when(refundItemRepository.findAllByRefundIdWithOrderItem(refund.getId()))
                .thenReturn(List.of(refundItem));
        when(productRepository.findAllByIdsWithLock(List.of(orderItem.getProductId())))
                .thenReturn(List.of());

        // when
        // then
        assertThatThrownBy(() -> refundProcessingTxService.complete(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    private RefundOutbox 환불Outbox(Long outboxId, Refund refund) {
        RefundOutbox outbox = RefundOutbox.create(refund, LocalDateTime.now());
        ReflectionTestUtils.setField(outbox, "id", outboxId);
        return outbox;
    }

    private Refund 환불(Payment payment) {
        Refund refund = Refund.createRefund(
                "refund-key-" + payment.getId(),
                "request-hash-" + payment.getId(),
                payment.getOrder(),
                payment,
                "환불 사유",
                1_000L,
                200L,
                800L,
                250L,
                850L,
                100L,
                50L,
                0L,
                50L
        );
        ReflectionTestUtils.setField(refund, "id", payment.getId() + 100L);
        return refund;
    }

    private Payment 결제(Long orderId, Long paymentId) {
        Order order = 주문(orderId);
        Payment payment = Payment.createPending(order, 1_000L, 200L, 800L, 8L);
        ReflectionTestUtils.setField(payment, "id", paymentId);
        return payment;
    }

    private Order 주문(Long orderId) {
        User user = User.create("user-" + orderId + "@example.com", "Password123!", "홍길동", "010-1234-5678");
        ReflectionTestUtils.setField(user, "id", orderId);
        Order order = Order.create(user, "ORDER-" + orderId, 1_000L, 200L);
        ReflectionTestUtils.setField(order, "id", orderId);
        return order;
    }

    private OrderItem 주문상품(Order order, Long orderItemId, int price, int quantity) {
        Product product = new Product(
                "테스트 상품",
                price,
                10,
                "테스트 상품 설명",
                ProductStatus.ON_SALE,
                ProductCategory.TOP
        );
        ReflectionTestUtils.setField(product, "id", orderItemId);
        OrderItem orderItem = new OrderItem(order, product, orderItemId, quantity);
        ReflectionTestUtils.setField(orderItem, "id", orderItemId);
        return orderItem;
    }
}
