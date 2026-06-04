package com.teamec2.paymentsystem.domain.point.service;

import com.teamec2.paymentsystem.domain.cart.repository.CartItemRepository;
import com.teamec2.paymentsystem.domain.cart.repository.CartRepository;
import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.repository.OrderItemRepository;
import com.teamec2.paymentsystem.domain.order.repository.OrderRepository;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.repository.PaymentRepository;
import com.teamec2.paymentsystem.domain.point.entity.PointTransaction;
import com.teamec2.paymentsystem.domain.point.enums.PointTransactionType;
import com.teamec2.paymentsystem.domain.point.repository.PointTransactionRepository;
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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    OrderItemRepository orderItemRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    PointTransactionRepository pointTransactionRepository;

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
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
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
    void 포인트예약_사용포인트가0이면_잔액과원장을변경하지않는다() {
        // given
        User user = 회원_저장(10000L);
        Order order = 주문_저장(user, 1000L, 0L);
        Payment payment = 결제_저장(order, 1000L, 0L, 1000L);

        // when
        pointService.reserveUsedPoints(payment);

        // then
        User foundUser = userRepository.findById(user.getId()).orElseThrow();

        assertThat(foundUser.getPointBalance()).isEqualTo(10000L);
        assertThat(pointTransactionRepository.count()).isZero();
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
    void 포인트예약_동시에두결제가같은잔액을사용하면_한건만성공한다() throws Exception {
        // given
        User user = 회원_저장(1000L);
        Order firstOrder = 주문_저장(user, 1000L, 700L);
        Order secondOrder = 주문_저장(user, 1000L, 700L);
        Payment firstPayment = 결제_저장(firstOrder, 1000L, 700L, 300L);
        Payment secondPayment = 결제_저장(secondOrder, 1000L, 700L, 300L);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        try {
            Future<ErrorCode> firstResult = executorService.submit(
                    () -> 포인트예약_실행결과(firstPayment, startLatch)
            );
            Future<ErrorCode> secondResult = executorService.submit(
                    () -> 포인트예약_실행결과(secondPayment, startLatch)
            );

            // when
            startLatch.countDown();

            // then
            assertThat(Arrays.asList(
                    firstResult.get(5, TimeUnit.SECONDS),
                    secondResult.get(5, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(null, ErrorCode.INSUFFICIENT_POINT);

            User foundUser = userRepository.findById(user.getId()).orElseThrow();

            assertThat(foundUser.getPointBalance()).isEqualTo(300L);
            assertThat(pointTransactionRepository.count()).isEqualTo(1);
            assertThat(pointTransactionRepository.findAll().get(0).getType())
                    .isEqualTo(PointTransactionType.USE_RESERVE);
        } finally {
            executorService.shutdownNow();
        }
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

    @Test
    void 포인트예약확정_같은결제를두번확정해도_원장은한개만유지된다() {
        // given
        User user = 회원_저장(10000L);
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        pointService.reserveUsedPoints(payment);

        // when
        pointService.confirmReservedPoints(payment);
        pointService.confirmReservedPoints(payment);

        // then
        User foundUser = userRepository.findById(user.getId()).orElseThrow();
        List<PointTransaction> pointTransactions = pointTransactionRepository.findAll();

        assertThat(foundUser.getPointBalance()).isEqualTo(9800L);
        assertThat(pointTransactions).hasSize(1);
        assertThat(pointTransactions.get(0).getType()).isEqualTo(PointTransactionType.USE);
        assertThat(pointTransactions.get(0).getIdempotencyKey())
                .isEqualTo("PAYMENT:%d:USE".formatted(payment.getId()));
    }

    @Test
    void 포인트예약확정_예약원장이없으면_POINT_ERROR_EXCEPTION이발생한다() {
        // given
        User user = 회원_저장(10000L);
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);

        // when
        // then
        assertThatThrownBy(() -> pointService.confirmReservedPoints(payment))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_ERROR_EXCEPTION);

        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isEqualTo(10000L);
        assertThat(pointTransactionRepository.count()).isZero();
    }

    @Test
    void 포인트예약확정_사용포인트가0이면_아무원장도필요하지않다() {
        // given
        User user = 회원_저장(10000L);
        Order order = 주문_저장(user, 1000L, 0L);
        Payment payment = 결제_저장(order, 1000L, 0L, 1000L);

        // when
        pointService.confirmReservedPoints(payment);

        // then
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isEqualTo(10000L);
        assertThat(pointTransactionRepository.count()).isZero();
    }

    @Test
    void 포인트예약_이미USE로확정된결제이면_다시예약하지않는다() {
        // given
        User user = 회원_저장(10000L);
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        pointService.reserveUsedPoints(payment);
        pointService.confirmReservedPoints(payment);

        // when
        pointService.reserveUsedPoints(payment);

        // then
        User foundUser = userRepository.findById(user.getId()).orElseThrow();
        List<PointTransaction> pointTransactions = pointTransactionRepository.findAll();

        assertThat(foundUser.getPointBalance()).isEqualTo(9800L);
        assertThat(pointTransactions).hasSize(1);
        assertThat(pointTransactions.get(0).getType()).isEqualTo(PointTransactionType.USE);
    }

    private ErrorCode 포인트예약_실행결과(Payment payment, CountDownLatch startLatch) {
        try {
            startLatch.await();
            pointService.reserveUsedPoints(payment);
            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
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
