package com.teamec2.paymentsystem.domain.payment.service;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.order.repository.OrderItemRepository;
import com.teamec2.paymentsystem.domain.order.repository.OrderRepository;
import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentRequest;
import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentResponse;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.repository.PaymentRepository;
import com.teamec2.paymentsystem.domain.point.service.PointService;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 결제 확정 과정에서 DB 상태 변경만 담당하는 트랜잭션 서비스.
 *
 * <p>{@link PaymentService}는 PortOne 같은 외부 API를 호출하는 Facade이고,
 * 이 클래스는 주문/결제 상태 변경처럼 DB 트랜잭션이 필요한 작업을 짧게 실행한다.
 *
 * <p>외부 API 호출과 DB 변경을 한 트랜잭션에 섞으면 트랜잭션 시간이 길어지고,
 * 내부 처리 실패 후 보상 취소를 남기기 어려워진다. 그래서 준비, 완료, 보상 후 실패 정리를
 * 각각 별도 메서드로 나눴다.
 */
@Service
@RequiredArgsConstructor
public class PaymentConfirmTxService {

    private static final boolean CART_CLEARED = false;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderItemRepository orderItemRepository;
    private final PointService pointService;

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
     * @return Facade가 외부 API 호출에 사용할 최소 결제 정보
     */
    @Transactional
    public ConfirmPaymentTarget prepare(Long userId, ConfirmPaymentRequest request) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        validateOrderOwner(order, userId);

        Payment payment = findPaymentByOrderIdForUpdate(order.getId());
        validateRequestedPayment(payment, request.portonePaymentId());

        if (payment.isCompleted()) {
            return new ConfirmPaymentTarget(
                    payment.getId(),
                    payment.getPortonePaymentId(),
                    payment.getPgAmount(),
                    payment.isPointOnly(),
                    ConfirmPaymentResponse.from(order, payment, CART_CLEARED)
            );
        }

        validateConfirmable(order, payment);

        return new ConfirmPaymentTarget(
                payment.getId(),
                payment.getPortonePaymentId(),
                payment.getPgAmount(),
                payment.isPointOnly(),
                null
        );
    }

    /**
     * 내부 주문과 결제를 완료 상태로 변경한다.
     *
     * <p>PortOne 검증이 끝난 뒤 호출되는 메서드다. 이 메서드는 하나의 트랜잭션 안에서
     * 주문 상태와 결제 상태를 함께 변경한다. 둘 중 하나라도 실패하면 전체 변경이 롤백된다.
     *
     * <p>이미 완료된 결제라면 중복 호출로 보고 상태를 다시 변경하지 않고 현재 응답을 반환한다.
     * 현재 PR 범위에서는 포인트 확정과 장바구니 초기화는 수행하지 않으므로 {@code cartCleared=false}를 유지한다.
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
            return ConfirmPaymentResponse.from(order, payment, CART_CLEARED);
        }

        validateConfirmable(order, payment);

        // 주문 생성 시 예약 차감한 USE_RESERVE 원장을 최종 사용 원장인 USE로 확정합니다.
        pointService.confirmReservedPoints(payment);

        payment.complete(approvedAt);
        order.complete();

        // 결제 완료 후 적립금이 추가됩니다.
        pointService.earnPoints(payment);

        return ConfirmPaymentResponse.from(order, payment, CART_CLEARED);
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

        if (!payment.isPending()) {
            return;
        }

        Order order = payment.getOrder();

        restoreStock(order);
        pointService.cancelReservedPoints(payment);
        order.cancelPendingPayment();
        payment.fail(LocalDateTime.now());
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
        List<OrderItem> orderItems = orderItemRepository.findAllWithProductByOrderId(order.getId());

        for (OrderItem orderItem : orderItems) {
            orderItem.getProduct().restoreStock(orderItem.getQuantity());
        }
    }

    private Payment findPaymentByOrderIdForUpdate(Long orderId) {
        return paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

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
        if (!Objects.equals(order.getUser().getId(), userId)) {
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
        if (!order.isPaymentPending()) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        if (!payment.isPending()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }
}
