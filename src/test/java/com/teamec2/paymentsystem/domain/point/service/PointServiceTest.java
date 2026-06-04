package com.teamec2.paymentsystem.domain.point.service;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.repository.OrderRepository;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.repository.PaymentRepository;
import com.teamec2.paymentsystem.domain.point.entity.PointTransaction;
import com.teamec2.paymentsystem.domain.point.enums.PointTransactionType;
import com.teamec2.paymentsystem.domain.point.repository.PointTransactionRepository;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.domain.user.repository.UserRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PointServiceTest {

    @Autowired
    PointService pointService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    PaymentRepository paymentRepository;

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

    private void clearDatabase() {
        pointTransactionRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 포인트예약_정상요청이면_잔액을예약차감하고_USE_RESERVE원장을생성한다() {
        // given
        User user = 회원_저장(10000L);
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);

        // when
        pointService.reserveUsedPoints(payment);

        // then
        User foundUser = userRepository.findById(user.getId()).orElseThrow();
        List<PointTransaction> pointTransactions = pointTransactionRepository.findAll();

        assertThat(foundUser.getPointBalance()).isEqualTo(9800L);
        assertThat(pointTransactions).hasSize(1);
        assertThat(pointTransactions.get(0).getType()).isEqualTo(PointTransactionType.USE_RESERVE);
        assertThat(pointTransactions.get(0).getAmount()).isEqualTo(200L);
        assertThat(pointTransactions.get(0).getIdempotencyKey())
                .isEqualTo("PAYMENT:%d:USE_RESERVE".formatted(payment.getId()));
    }

    @Test
    void 포인트예약_같은결제를두번처리해도_잔액을한번만차감한다() {
        // given
        User user = 회원_저장(10000L);
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);

        // when
        pointService.reserveUsedPoints(payment);
        pointService.reserveUsedPoints(payment);

        // then
        User foundUser = userRepository.findById(user.getId()).orElseThrow();

        assertThat(foundUser.getPointBalance()).isEqualTo(9800L);
        assertThat(pointTransactionRepository.count()).isEqualTo(1);
    }

    @Test
    void 포인트예약_잔액부족이면_INSUFFICIENT_POINT가발생하고_원장을생성하지않는다() {
        // given
        User user = 회원_저장(100L);
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);

        // when
        // then
        assertThatThrownBy(() -> pointService.reserveUsedPoints(payment))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_POINT);

        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isEqualTo(100L);
        assertThat(pointTransactionRepository.count()).isZero();
    }

    @Test
    void 포인트예약확정_예약원장을_USE로변경하고_잔액은추가차감하지않는다() {
        // given
        User user = 회원_저장(10000L);
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        pointService.reserveUsedPoints(payment);

        // when
        pointService.confirmReservedPoints(payment);

        // then
        User foundUser = userRepository.findById(user.getId()).orElseThrow();
        List<PointTransaction> pointTransactions = pointTransactionRepository.findAll();

        assertThat(foundUser.getPointBalance()).isEqualTo(9800L);
        assertThat(pointTransactions).hasSize(1);
        assertThat(pointTransactions.get(0).getType()).isEqualTo(PointTransactionType.USE);
        assertThat(pointTransactions.get(0).getAmount()).isEqualTo(200L);
        assertThat(pointTransactions.get(0).getIdempotencyKey())
                .isEqualTo("PAYMENT:%d:USE".formatted(payment.getId()));
    }

    private User 회원_저장(Long pointBalance) {
        User user = User.create(uniqueEmail(), "Password123!", "홍길동", "010-1234-5678");

        if (pointBalance > 0) {
            user.increasePointBalance(pointBalance);
        }

        return userRepository.save(user);
    }

    private Order 주문_저장(User user, Long totalAmount, Long usedPoint) {
        return orderRepository.save(Order.create(user, uniqueOrderNumber(), totalAmount, usedPoint));
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
