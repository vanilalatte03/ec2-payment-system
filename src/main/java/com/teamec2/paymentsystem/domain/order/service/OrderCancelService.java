package com.teamec2.paymentsystem.domain.order.service;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.order.facade.OrderProductFacade;
import com.teamec2.paymentsystem.domain.order.repository.OrderItemRepository;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 결제 완료 전 주문 취소를 담당하는 주문 도메인 서비스.
 *
 * 주문 생성 시점에는 재고를 먼저 차감한다. 그래서 결제 확정 보상 취소처럼
 * 결제 완료 전에 주문을 되돌리는 흐름에서는 주문 상품 취소와 재고 복구가 함께 일어나야 한다.
 */
@Service
@RequiredArgsConstructor
public class OrderCancelService {

    private final OrderItemRepository orderItemRepository;
    private final OrderProductFacade orderProductFacade;

    /**
     * 결제 완료 전에 주문을 취소하고, 아직 취소되지 않은 주문 상품의 재고를 복구한다.
     *
     * 결제 도메인은 보상 흐름만 조율하고, 주문 상품과 재고 복구는 주문 도메인이 처리하도록
     * 이 메서드에 모아둔다.
     *
     * @param order 결제 완료 전에 취소할 주문
     */
    @Transactional
    public void cancelBeforePayment(Order order) {
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());

        if (orderItems.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND);
        }

        List<OrderItem> cancelTargets = orderItems.stream()
                .filter(orderItem -> !orderItem.isCanceled())
                .toList();

        restoreStocks(cancelTargets);
        order.cancelBeforePayment();
    }

    /**
     * 주문 상품에 연결된 상품 row를 잠근 뒤 재고를 복구한다.
     *
     * 재고 복구도 재고 차감처럼 동시에 같은 상품을 수정할 수 있으므로, 최신 상품 row를
     * 비관락으로 다시 조회한 뒤 {@link OrderItem#cancel(int, Product)}을 호출한다.
     */
    private void restoreStocks(List<OrderItem> orderItems) {
        if (orderItems.isEmpty()) {
            return;
        }

        Map<Long, Product> lockedProducts = lockProducts(orderItems);

        for (OrderItem orderItem : orderItems) {
            Product lockedProduct = lockedProducts.get(orderItem.getProductId());
            orderItem.cancel(orderItem.getQuantity(), lockedProduct);
        }
    }

    /**
     * 재고 복구 대상 상품을 ID 오름차순으로 잠근다.
     *
     * {@link OrderProductFacade#lockProducts(List)} 내부에서 중복 제거와 정렬을 해주므로,
     * 여러 주문 상품이 같은 상품을 가리켜도 같은 상품 row를 한 번만 잠근다.
     */
    private Map<Long, Product> lockProducts(List<OrderItem> orderItems) {
        List<Long> productIds = orderItems.stream()
                .map(OrderItem::getProductId)
                .toList();

        return orderProductFacade.lockProducts(productIds);
    }
}
