package com.teamec2.paymentsystem.domain.point.service;

import com.teamec2.paymentsystem.domain.cart.repository.CartItemRepository;
import com.teamec2.paymentsystem.domain.cart.repository.CartRepository;
import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.repository.OrderItemRepository;
import com.teamec2.paymentsystem.domain.order.repository.OrderRepository;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.repository.PaymentRepository;
import com.teamec2.paymentsystem.domain.point.dto.PointBalanceResponse;
import com.teamec2.paymentsystem.domain.point.dto.PointTransactionResponse;
import com.teamec2.paymentsystem.domain.point.enums.PointTransactionType;
import com.teamec2.paymentsystem.domain.point.repository.PointTransactionRepository;
import com.teamec2.paymentsystem.domain.product.repository.ProductRepository;
import com.teamec2.paymentsystem.domain.refund.repository.RefundTestRepository;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.domain.user.repository.UserRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import com.teamec2.paymentsystem.global.pagination.PageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PointQueryServiceTest {

    @Autowired
    PointQueryService pointQueryService;

    @Autowired
    PointService pointService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderItemRepository orderItemRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    PointTransactionRepository pointTransactionRepository;

    @Autowired
    RefundTestRepository refundTestRepository;

    @Autowired
    CartItemRepository cartItemRepository;

    @Autowired
    CartRepository cartRepository;

    @Autowired
    ProductRepository productRepository;

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
        refundTestRepository.deleteAll();
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 포인트잔액조회_스냅샷과원장이일치하면_현재잔액을반환한다() {
        // given
        User user = 회원_저장(0L);
        Payment payment = 결제_저장(주문_저장(user, 1000L, 0L), 1000L, 0L, 1000L);
        pointService.earnPoints(payment);

        // when
        PointBalanceResponse response = pointQueryService.getPointBalance(user.getId());

        // then
        assertThat(response.getUserId()).isEqualTo(user.getId());
        assertThat(response.getBalance()).isEqualTo(10L);
    }

    @Test
    void 포인트잔액조회_스냅샷과원장이불일치하면_POINT_LEDGER_SYNC_FAILED가발생한다() {
        // given
        User user = 회원_저장(100L);

        // when
        // then
        assertThatThrownBy(() -> pointQueryService.getPointBalance(user.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_LEDGER_SYNC_FAILED);
    }

    @Test
    void 포인트거래내역조회_거래타입으로필터링해_최신순페이지를반환한다() {
        // given
        User user = 회원_저장(200L);
        Payment pointCardPayment = 결제_저장(주문_저장(user, 1000L, 200L), 1000L, 200L, 800L);
        Payment cardPayment = 결제_저장(주문_저장(user, 1000L, 0L), 1000L, 0L, 1000L);

        pointService.reserveUsedPoints(pointCardPayment);
        pointService.confirmReservedPoints(pointCardPayment);
        pointService.earnPoints(pointCardPayment);
        pointService.earnPoints(cardPayment);

        // when
        PageResponse<PointTransactionResponse> response = pointQueryService.getPointTransaction(
                user.getId(),
                PointTransactionType.EARN,
                0,
                10
        );

        // then
        assertThat(response.content()).hasSize(2);
        assertThat(response.totalElements()).isEqualTo(2L);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.content())
                .extracting(PointTransactionResponse::getType)
                .containsOnly(PointTransactionType.EARN);
    }

    @Test
    void 포인트거래내역조회_존재하지않는회원이면_POINT_ACCOUNT_NOT_FOUND가발생한다() {
        // given
        Long notFoundUserId = 999999L;

        // when
        // then
        assertThatThrownBy(() -> pointQueryService.getPointTransaction(
                notFoundUserId,
                null,
                0,
                10
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_ACCOUNT_NOT_FOUND);
    }

    private User 회원_저장(Long pointBalance) {
        User user = User.create(uniqueEmail(), "Password123!", "홍길동", "010-1234-5678");

        // 테스트에서 필요한 초기 포인트 스냅샷을 직접 구성합니다.
        if (pointBalance > 0) {
            user.increasePointBalance(pointBalance);
        }

        return userRepository.save(user);
    }

    private Order 주문_저장(User user, Long totalAmount, Long usedPointAmount) {
        return orderRepository.save(Order.create(user, uniqueOrderNumber(), totalAmount, usedPointAmount));
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
