package com.teamec2.paymentsystem.domain.payment.service;

import com.teamec2.paymentsystem.domain.cart.dto.ClearCartResponse;
import com.teamec2.paymentsystem.domain.cart.service.CartService;
import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.order.service.OrderService;
import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentRequest;
import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentResponse;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.facade.PaymentFacade;
import com.teamec2.paymentsystem.domain.payment.facade.ConfirmPaymentTarget;
import com.teamec2.paymentsystem.domain.payment.repository.PaymentRepository;
import com.teamec2.paymentsystem.domain.point.service.PointService;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.service.ProductService;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 결제 도메인의 DB 조회와 상태 변경을 담당하는 트랜잭션 서비스.
 *
 * <p>{@link PaymentFacade}는 PortOne 같은 외부 API를 호출하고 전체 순서를 조율한다.
 * 이 클래스는 결제 확정 대상 준비, 내부 완료 처리, 보상 취소 후 실패 정리처럼
 * DB 트랜잭션이 필요한 작업만 짧게 실행한다.
 *
 * <p>다른 도메인의 repository를 직접 참조하지 않고 {@link OrderService},
 * {@link ProductService}, {@link CartService}, {@link PointService}를 통해 필요한
 * 도메인 작업을 요청한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderService orderService;
    private final PaymentRepository paymentRepository;
    private final PointService pointService;
    private final ProductService productService;
    private final CartService cartService;

    /**
     * 결제 확정 대상 주문과 결제를 조회하고, 확정 가능한 상태인지 검증한다.
     *
     * <p>이 단계에서는 아직 PortOne API를 호출하지 않고, 우리 DB 기준으로만 다음을 확인한다.
     * <ul>
     *     <li>주문이 존재하는지</li>
     *     <li>요청 사용자가 주문 소유자인지</li>
     *     <li>요청한 PortOne 결제 ID가 우리 결제 레코드와 일치하는지</li>
     *     <li>주문/결제가 아직 확정 가능한 대기 상태인지</li>
     * </ul>
     *
     * <p>결제 레코드는 비관적 쓰기 잠금으로 조회한다. 같은 결제를 여러 요청이 동시에
     * 확정하려고 할 때 한 요청만 상태를 바꿀 수 있게 하기 위해서다.
     *
     * @param userId 결제 확정을 요청한 사용자 ID
     * @param request 결제 확정 요청 정보
     * @return 퍼사드가 외부 API 호출에 사용할 최소 결제 정보
     */
    @Transactional
    public ConfirmPaymentTarget prepare(Long userId, ConfirmPaymentRequest request) {
        Order order = orderService.findOrderEntity(request.orderId());
        validateOrderOwner(order, userId);

        Payment payment = findPaymentByOrderIdForUpdate(order.getId());
        validateRequestedPayment(payment, request.portonePaymentId());

        if (payment.isCompleted()) {
            return completedTarget(order, payment);
        }

        if (payment.requiresCompensationCleanup() || payment.hasCompensationResultUnknown()) {
            return pendingTarget(payment);
        }

        validateConfirmable(order, payment);

        return pendingTarget(payment);
    }

    /**
     * 웹훅으로 들어온 PortOne 결제 ID를 기준으로 결제 확정 대상을 준비한다.
     *
     * <p>클라이언트 확정 요청과 달리 사용자 ID나 주문 ID를 받지 않으므로 PortOne 결제 ID로
     * 결제 레코드를 조회한다. 조회한 결제에는 쓰기 잠금을 걸어 웹훅 재전송이나 클라이언트 확정 요청과
     * 동시에 같은 결제가 완료 처리되지 않도록 한다.
     *
     * <p>이미 완료된 결제라면 멱등 응답을 만들 수 있도록 완료 응답을 포함한 대상을 반환한다.
     *
     * @param portonePaymentId PortOne 결제 ID
     * @return 퍼사드가 웹훅 확정 처리에 사용할 최소 결제 정보
     */
    @Transactional
    public ConfirmPaymentTarget prepareByPortonePaymentId(String portonePaymentId) {
        Payment payment = paymentRepository.findByPortonePaymentIdForUpdate(portonePaymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        Order order = payment.getOrder();

        if (payment.isCompleted()) {
            return completedTarget(order, payment);
        }

        if (payment.requiresCompensationCleanup() || payment.hasCompensationResultUnknown()) {
            return pendingTarget(payment);
        }

        validateConfirmable(order, payment);

        return pendingTarget(payment);
    }

    /**
     * 내부 주문과 결제를 완료 상태로 변경한다.
     *
     * <p>PortOne 검증이 끝난 뒤 호출되는 메서드다. 이 메서드는 하나의 트랜잭션 안에서
     * 주문 상태와 결제 상태를 함께 변경한다. 둘 중 하나라도 실패하면 전체 변경이 롤백된다.
     *
     * <p>이미 완료된 결제라면 중복 호출로 보고 상태를 다시 변경하지 않고 현재 응답을 반환한다.
     * 신규 완료 처리에서는 포인트 예약 차감을 확정하고, 결제 상태를 완료로 바꾸고,
     * 적립 포인트와 장바구니 정리 결과를 기록한다.
     *
     * @param paymentId 완료 처리할 내부 결제 ID
     * @param approvedAt PortOne 승인 시각 또는 포인트 전액 결제의 내부 확정 시각
     * @return 결제 확정 응답
     */
    @Transactional
    public ConfirmPaymentResponse complete(Long paymentId, LocalDateTime approvedAt) {
        Payment payment = findPaymentByIdForUpdate(paymentId);
        Order order = payment.getOrder();

        if (payment.isCompleted()) {
            return ConfirmPaymentResponse.from(order, payment, payment.isCartCleared());
        }

        validateConfirmable(order, payment);
        confirmPaymentState(payment, order, approvedAt);
        recordRewardAndCartCleanup(payment, order);

        return ConfirmPaymentResponse.from(order, payment, payment.isCartCleared());
    }

    /**
     * PortOne 보상 취소가 성공한 뒤 내부 주문/결제를 실패 상태로 정리한다.
     *
     * <p>{@code Propagation.REQUIRES_NEW}는 항상 새 트랜잭션을 시작하겠다는 의미다.
     * 내부 완료 처리 트랜잭션이 실패해서 롤백된 뒤에도, 보상 취소 성공 사실은 별도 트랜잭션으로
     * DB에 남겨야 한다. 그래서 이 메서드는 기존 트랜잭션에 참여하지 않고 새 트랜잭션으로 실행한다.
     *
     * <p>정리 순서는 다음과 같다.
     * <ol>
     *     <li>주문 생성 때 선차감했던 재고를 복구한다.</li>
     *     <li>주문 생성 때 예약 차감했던 포인트를 복구한다.</li>
     *     <li>주문을 결제대기에서 취소 상태로 변경한다.</li>
     *     <li>결제를 실패 상태로 변경한다.</li>
     * </ol>
     *
     * @param paymentId 보상 취소된 내부 결제 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failAfterCompensation(Long paymentId) {
        Payment payment = findPaymentByIdForUpdate(paymentId);

        if (payment.isFailed()) {
            return;
        }

        if (!payment.isPending() && !payment.requiresCompensationCleanup()) {
            return;
        }

        Order order = payment.getOrder();

        restoreStock(order);
        pointService.cancelReservedPoints(payment);
        order.cancelPendingPayment();
        payment.fail(LocalDateTime.now());
    }

    /**
     * PortOne 보상 취소 성공 사실을 내부 정리 전에 별도 트랜잭션으로 기록한다.
     *
     * <p>이 표시가 남아 있으면 이후 결제 확정 재시도나 취소 웹훅에서 PortOne 재취소 없이
     * 내부 주문/결제 실패 정리만 다시 실행할 수 있다.
     *
     * @param paymentId 보상 취소된 내부 결제 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompensationRequired(Long paymentId) {
        Payment payment = findPaymentByIdForUpdate(paymentId);

        if (payment.isFailed()) {
            return;
        }

        payment.markCompensationRequired();
    }

    /**
     * PortOne 보상 취소 요청 결과를 확정하지 못한 결제를 운영 확인/재조회 대상으로 표시한다.
     *
     * @param paymentId 보상 취소 결과를 확정하지 못한 내부 결제 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompensationResultUnknown(Long paymentId) {
        Payment payment = findPaymentByIdForUpdate(paymentId);

        if (payment.isFailed()) {
            return;
        }

        payment.markCompensationResultUnknown();
    }

    /**
     * 결제 ID로 결제 엔티티를 조회한다.
     *
     * <p>웹훅 이벤트가 처리 완료된 결제와 연관관계를 맺을 때 사용한다.
     * 상태 변경 목적의 조회가 아니므로 비관락을 걸지 않는다.
     *
     * @param paymentId 조회할 내부 결제 ID
     * @return 결제 엔티티
     */
    @Transactional(readOnly = true)
    public Payment findPaymentById(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    /**
     * 이미 완료된 결제에 대한 멱등 응답 대상을 만든다.
     *
     * @param order 결제와 연결된 주문
     * @param payment 완료된 결제
     * @return 완료 응답을 포함한 결제 확정 대상
     */
    private ConfirmPaymentTarget completedTarget(Order order, Payment payment) {
        return new ConfirmPaymentTarget(
                payment.getId(),
                payment.getPortonePaymentId(),
                payment.getPgAmount(),
                payment.isPointOnly(),
                ConfirmPaymentResponse.from(order, payment, payment.isCartCleared()),
                payment.getStatus()
        );
    }

    /**
     * 아직 확정되지 않은 결제에 대한 처리 대상을 만든다.
     *
     * @param payment 확정 대기 중인 결제
     * @return 외부 검증과 내부 완료 처리에 필요한 최소 결제 정보
     */
    private ConfirmPaymentTarget pendingTarget(Payment payment) {
        return new ConfirmPaymentTarget(
                payment.getId(),
                payment.getPortonePaymentId(),
                payment.getPgAmount(),
                payment.isPointOnly(),
                null,
                payment.getStatus()
        );
    }

    /**
     * 예약 포인트를 실제 사용으로 확정하고 주문/결제를 완료 상태로 변경한다.
     *
     * @param payment 완료 처리할 결제
     * @param order 완료 처리할 주문
     * @param approvedAt 결제 승인 시각
     */
    private void confirmPaymentState(Payment payment, Order order, LocalDateTime approvedAt) {
        pointService.confirmReservedPoints(payment);
        payment.complete(approvedAt);
        order.complete();
    }

    /**
     * 결제 완료 후 적립 포인트를 지급하고 주문에 포함된 장바구니 상품을 삭제한 결과를 기록한다.
     *
     * @param payment 완료 처리된 결제
     * @param order 완료 처리된 주문
     */
    private void recordRewardAndCartCleanup(Payment payment, Order order) {
        pointService.earnPoints(payment);

        ClearCartResponse clearCartResponse = cartService.clearPurchasedItemIds(
                order.getUser().getId(),
                findSourceCartItemIds(order)
        );

        payment.recordCartCleared(clearCartResponse.deletedCount() > 0);
    }

    /**
     * 주문 상품에서 결제 완료 후 삭제할 원본 장바구니 상품 ID를 추출한다.
     *
     * @param order 주문 상품을 조회할 주문
     * @return 중복이 제거된 원본 장바구니 상품 ID 목록
     */
    private List<Long> findSourceCartItemIds(Order order) {
        return orderService.findOrderItemsByOrderId(order.getId()).stream()
                .map(OrderItem::getSourceCartItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * 주문 생성 시점에 미리 차감했던 상품 재고를 복구한다.
     *
     * <p>이 프로젝트의 주문 생성 흐름은 결제 완료 전 재고를 먼저 차감한다.
     * 그래서 외부 결제를 보상 취소한 경우에는 주문 상품 수량만큼 재고를 되돌려야 한다.
     *
     * @param order 재고를 복구할 주문
     */
    private void restoreStock(Order order) {
        List<OrderItem> restorableOrderItems = orderService.findOrderItemsByOrderId(order.getId()).stream()
                .filter(orderItem -> !orderItem.isCanceled())
                .toList();
        Map<Long, Product> lockedProducts = lockProductsForStockRestore(restorableOrderItems);

        for (OrderItem orderItem : restorableOrderItems) {
            Product product = lockedProducts.get(orderItem.getProductId());
            product.restoreStock(orderItem.getQuantity());
        }
    }

    /**
     * 재고 복구 대상 상품을 항상 ID 오름차순으로 잠근다.
     *
     * <p>여러 상품을 동시에 복구할 때 모든 트랜잭션이 같은 순서로 잠금을 획득하면
     * 교착상태 위험을 낮출 수 있다.
     *
     * @param orderItems 재고를 복구할 주문 상품 목록
     * @return 상품 ID를 key로 하는 잠금 획득 완료 상품 맵
     */
    private Map<Long, Product> lockProductsForStockRestore(List<OrderItem> orderItems) {
        List<Long> productIds = orderItems.stream()
                .map(OrderItem::getProductId)
                .distinct()
                .sorted()
                .toList();

        return productService.findProductsByIdsForUpdate(productIds);
    }

    /**
     * 주문 ID 기준으로 결제를 조회하면서 쓰기 잠금을 획득한다.
     *
     * @param orderId 결제와 연결된 주문 ID
     * @return 잠금이 적용된 결제
     */
    private Payment findPaymentByOrderIdForUpdate(Long orderId) {
        return paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    /**
     * 결제 ID 기준으로 결제를 조회하면서 쓰기 잠금을 획득한다.
     *
     * @param paymentId 내부 결제 ID
     * @return 잠금이 적용된 결제
     */
    private Payment findPaymentByIdForUpdate(Long paymentId) {
        return paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    /**
     * 결제 확정 요청자가 주문 소유자인지 확인한다.
     *
     * @param order 소유권을 확인할 주문
     * @param userId 요청 사용자 ID
     */
    private void validateOrderOwner(Order order, Long userId) {
        if (!order.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }
    }

    /**
     * 클라이언트가 보낸 PortOne 결제 ID가 주문에 연결된 결제 ID와 같은지 확인한다.
     *
     * <p>다른 주문의 결제 ID로 확정 요청하는 것을 막기 위한 검증이다.
     *
     * @param payment 내부 결제 엔티티
     * @param portonePaymentId 요청으로 받은 PortOne 결제 ID
     */
    private void validateRequestedPayment(Payment payment, String portonePaymentId) {
        if (!payment.getPortonePaymentId().equals(portonePaymentId)) {
            throw new BusinessException(ErrorCode.PAYMENT_PORTONE_ID_MISMATCH);
        }
    }

    /**
     * 주문과 결제가 아직 확정 가능한 대기 상태인지 확인한다.
     *
     * <p>주문이 이미 완료/취소됐거나 결제가 이미 실패/환불 상태라면
     * 결제 확정을 다시 진행하면 안 된다.
     *
     * @param order 확정 대상 주문
     * @param payment 확정 대상 결제
     */
    private void validateConfirmable(Order order, Payment payment) {
        if (!order.isPaymentConfirmable()) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        if (!payment.isPending()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }
}
