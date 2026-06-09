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
import com.teamec2.paymentsystem.domain.refund.entity.Refund;
import com.teamec2.paymentsystem.domain.refund.repository.RefundTestRepository;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.domain.user.repository.UserRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void 주문취소포인트복구_예약원장이있으면_잔액을복구하고_USE_CANCEL원장을생성한다() {
        // given
        User user = 회원_저장(10000L);
        Order order = 주문_저장(user, 1000L, 500L);
        Payment payment = 결제_저장(order, 1000L, 500L, 500L);
        pointService.reserveUsedPoints(payment);

        // when
        pointService.restoreReservedPointsForOrderCancel(payment, 200L, List.of("1:1:1"));

        // then
        User foundUser = userRepository.findById(user.getId()).orElseThrow();
        List<PointTransaction> pointTransactions = pointTransactionRepository.findAll();
        PointTransaction cancelTransaction = pointTransactions.stream()
                .filter(pointTransaction -> pointTransaction.getType() == PointTransactionType.USE_CANCEL)
                .findFirst()
                .orElseThrow();

        assertThat(foundUser.getPointBalance()).isEqualTo(9700L);
        assertThat(pointTransactions).hasSize(2);
        assertThat(cancelTransaction.getAmount()).isEqualTo(200L);
    }

    @Test
    void 주문취소포인트복구_복구금액이있는데_예약원장이없으면_POINT_ERROR_EXCEPTION이발생한다() {
        // given
        User user = 회원_저장(10000L);
        Order order = 주문_저장(user, 1000L, 500L);
        Payment payment = 결제_저장(order, 1000L, 500L, 500L);

        // when
        // then
        assertThatThrownBy(() -> pointService.restoreReservedPointsForOrderCancel(payment, 200L, List.of("1:1:1")))
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

    @Test
    void 포인트적립_정상요청이면_잔액을증가하고_EARN원장을생성한다() {
        // given
        User user = 회원_저장(100L);
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);

        // when
        pointService.earnPoints(payment);

        // then
        User foundUser = userRepository.findById(user.getId()).orElseThrow();
        List<PointTransaction> pointTransactions = pointTransactionRepository.findAll();

        assertThat(foundUser.getPointBalance()).isEqualTo(108L);
        assertThat(pointTransactions).hasSize(1);
        assertThat(pointTransactions.get(0).getType()).isEqualTo(PointTransactionType.EARN);
        assertThat(pointTransactions.get(0).getAmount()).isEqualTo(8L);
        assertThat(pointTransactions.get(0).getIdempotencyKey())
                .isEqualTo("PAYMENT:%d:EARN".formatted(payment.getId()));
    }

    @Test
    void 포인트적립_같은결제를두번처리해도_한번만적립한다() {
        // given
        User user = 회원_저장(100L);
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);

        // when
        pointService.earnPoints(payment);
        pointService.earnPoints(payment);

        // then
        User foundUser = userRepository.findById(user.getId()).orElseThrow();

        assertThat(foundUser.getPointBalance()).isEqualTo(108L);
        assertThat(pointTransactionRepository.count()).isEqualTo(1);
    }

    @Test
    void 포인트적립_적립금액이0원이면_잔액과원장을변경하지않는다() {
        // given
        User user = 회원_저장(100L);
        Order order = 주문_저장(user, 1_000L, 1_000L);
        Payment payment = 결제_저장(order, 1_000L, 1_000L, 0L);

        // when
        pointService.earnPoints(payment);

        // then
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isEqualTo(100L);
        assertThat(pointTransactionRepository.count()).isZero();
    }

    @Test
    void 포인트적립_요청값이없거나금액이음수이면_예외가발생한다() {
        // given
        Payment rewardNullPayment = mock(Payment.class);
        Payment rewardNegativePayment = mock(Payment.class);

        when(rewardNullPayment.getId()).thenReturn(1L);
        when(rewardNullPayment.getRewardPointAmount()).thenReturn(null);
        when(rewardNegativePayment.getId()).thenReturn(2L);
        when(rewardNegativePayment.getRewardPointAmount()).thenReturn(-1L);

        // when
        // then
        assertThatThrownBy(() -> pointService.earnPoints(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
        assertThatThrownBy(() -> pointService.earnPoints(rewardNullPayment))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
        assertThatThrownBy(() -> pointService.earnPoints(rewardNegativePayment))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_POINT_TRANSACTION_AMOUNT);
    }

    @Test
    void 포인트적립_결제회원이없으면_USER_NOT_FOUND가발생한다() {
        // given
        User unsavedUser = User.create(uniqueEmail(), "Password123!", "홍길동", "010-1234-5678");
        ReflectionTestUtils.setField(unsavedUser, "id", 999_999L);
        Order order = Order.create(unsavedUser, uniqueOrderNumber(), 1_000L, 200L);
        Payment payment = Payment.createPending(order, 1_000L, 200L, 800L, 8L);
        ReflectionTestUtils.setField(payment, "id", 1L);

        // when
        // then
        assertThatThrownBy(() -> pointService.earnPoints(payment))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        assertThat(pointTransactionRepository.count()).isZero();
    }

    @Test
    void 회원가입보너스_정상요청이면_잔액을증가하고_SIGNUP_BONUS원장을생성한다() {
        // given
        User user = 회원_저장(0L);

        // when
        pointService.grantSignupBonus(user);

        // then
        List<PointTransaction> pointTransactions = pointTransactionRepository.findAll();

        assertThat(user.getPointBalance()).isEqualTo(PointService.SIGNUP_BONUS_POINT_AMOUNT);
        assertThat(pointTransactions).hasSize(1);
        assertThat(pointTransactions.get(0).getType()).isEqualTo(PointTransactionType.SIGNUP_BONUS);
        assertThat(pointTransactions.get(0).getAmount()).isEqualTo(PointService.SIGNUP_BONUS_POINT_AMOUNT);
        assertThat(pointTransactions.get(0).getIdempotencyKey())
                .isEqualTo("SIGNUP_BONUS:%d".formatted(user.getId()));
    }

    @Test
    void 회원가입보너스_같은회원에게두번지급해도_한번만적립한다() {
        // given
        User user = 회원_저장(0L);

        // when
        pointService.grantSignupBonus(user);
        pointService.grantSignupBonus(user);

        // then
        assertThat(user.getPointBalance()).isEqualTo(PointService.SIGNUP_BONUS_POINT_AMOUNT);
        assertThat(pointTransactionRepository.count()).isEqualTo(1);
    }

    @Test
    void 회원가입보너스_회원이없거나ID가없으면_MISSING_REQUIRED_FIELD가발생한다() {
        // given
        User unsavedUser = User.create(uniqueEmail(), "Password123!", "홍길동", "010-1234-5678");

        // when
        // then
        assertThatThrownBy(() -> pointService.grantSignupBonus(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
        assertThatThrownBy(() -> pointService.grantSignupBonus(unsavedUser))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
    }

    @Test
    void 예약포인트취소_예약원장이있으면_잔액을복구하고_USE_CANCEL원장을생성한다() {
        // given
        User user = 회원_저장(1000L);
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        pointService.reserveUsedPoints(payment);

        // when
        pointService.cancelReservedPoints(payment);

        // then
        User foundUser = userRepository.findById(user.getId()).orElseThrow();
        List<PointTransaction> pointTransactions = pointTransactionRepository.findAll();

        assertThat(foundUser.getPointBalance()).isEqualTo(1000L);
        assertThat(pointTransactions).hasSize(2);
        assertThat(pointTransactions)
                .extracting(PointTransaction::getType)
                .containsExactlyInAnyOrder(PointTransactionType.USE_RESERVE, PointTransactionType.USE_CANCEL);
        assertThat(pointTransactions)
                .extracting(PointTransaction::getIdempotencyKey)
                .containsExactlyInAnyOrder(
                        "PAYMENT:%d:USE_RESERVE".formatted(payment.getId()),
                        "PAYMENT:%d:USE_CANCEL".formatted(payment.getId())
                );
    }

    @Test
    void 예약포인트취소_같은결제를두번취소해도_한번만복구한다() {
        // given
        User user = 회원_저장(1_000L);
        Order order = 주문_저장(user, 1_000L, 200L);
        Payment payment = 결제_저장(order, 1_000L, 200L, 800L);
        pointService.reserveUsedPoints(payment);

        // when
        pointService.cancelReservedPoints(payment);
        pointService.cancelReservedPoints(payment);

        // then
        User foundUser = userRepository.findById(user.getId()).orElseThrow();

        assertThat(foundUser.getPointBalance()).isEqualTo(1_000L);
        assertThat(pointTransactionRepository.count()).isEqualTo(2);
    }

    @Test
    void 예약포인트취소_예약원장이없으면_POINT_ERROR_EXCEPTION이발생한다() {
        // given
        User user = 회원_저장(1_000L);
        Order order = 주문_저장(user, 1_000L, 200L);
        Payment payment = 결제_저장(order, 1_000L, 200L, 800L);

        // when
        // then
        assertThatThrownBy(() -> pointService.cancelReservedPoints(payment))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_ERROR_EXCEPTION);

        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isEqualTo(1_000L);
        assertThat(pointTransactionRepository.count()).isZero();
    }

    @Test
    void 포인트예약_요청값이없거나금액이음수이면_예외가발생한다() {
        // given
        Payment usedPointNullPayment = mock(Payment.class);
        Payment usedPointNegativePayment = mock(Payment.class);

        when(usedPointNullPayment.getId()).thenReturn(1L);
        when(usedPointNullPayment.getUsedPointAmount()).thenReturn(null);
        when(usedPointNegativePayment.getId()).thenReturn(2L);
        when(usedPointNegativePayment.getUsedPointAmount()).thenReturn(-1L);

        // when
        // then
        assertThatThrownBy(() -> pointService.reserveUsedPoints(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
        assertThatThrownBy(() -> pointService.reserveUsedPoints(usedPointNullPayment))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
        assertThatThrownBy(() -> pointService.reserveUsedPoints(usedPointNegativePayment))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_POINT_TRANSACTION_AMOUNT);
    }

    @Test
    void 사용포인트복구_환불요청이면_잔액을증가하고_USE_RESTORE원장을생성한다() {
        // given
        User user = 회원_저장(0L);
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        Refund refund = 환불_저장(payment);

        // when
        pointService.restoreUsedPoints(payment, refund, 200L);

        // then
        User foundUser = userRepository.findById(user.getId()).orElseThrow();
        List<PointTransaction> pointTransactions = pointTransactionRepository.findAll();

        assertThat(foundUser.getPointBalance()).isEqualTo(200L);
        assertThat(pointTransactions).hasSize(1);
        assertThat(pointTransactions.get(0).getType()).isEqualTo(PointTransactionType.USE_RESTORE);
        assertThat(pointTransactions.get(0).getAmount()).isEqualTo(200L);
        assertThat(pointTransactions.get(0).getIdempotencyKey())
                .isEqualTo("REFUND:%d:USE_RESTORE".formatted(refund.getId()));
    }

    @Test
    void 사용포인트복구_복구금액이0원이면_잔액과원장을변경하지않는다() {
        // given
        User user = 회원_저장(0L);
        Order order = 주문_저장(user, 1_000L, 200L);
        Payment payment = 결제_저장(order, 1_000L, 200L, 800L);
        Refund refund = 환불_저장(payment);

        // when
        pointService.restoreUsedPoints(payment, refund, 0L);

        // then
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isZero();
        assertThat(pointTransactionRepository.count()).isZero();
    }

    @Test
    void 사용포인트복구_같은환불을두번처리해도_한번만복구한다() {
        // given
        User user = 회원_저장(0L);
        Order order = 주문_저장(user, 1_000L, 200L);
        Payment payment = 결제_저장(order, 1_000L, 200L, 800L);
        Refund refund = 환불_저장(payment);

        // when
        pointService.restoreUsedPoints(payment, refund, 200L);
        pointService.restoreUsedPoints(payment, refund, 200L);

        // then
        User foundUser = userRepository.findById(user.getId()).orElseThrow();

        assertThat(foundUser.getPointBalance()).isEqualTo(200L);
        assertThat(pointTransactionRepository.count()).isEqualTo(1);
    }

    @Test
    void 사용포인트복구_환불의결제와요청결제가다르면_VALIDATION_FAILED가발생한다() {
        // given
        User user = 회원_저장(0L);
        Order firstOrder = 주문_저장(user, 1_000L, 200L);
        Order secondOrder = 주문_저장(user, 1_000L, 200L);
        Payment firstPayment = 결제_저장(firstOrder, 1_000L, 200L, 800L);
        Payment secondPayment = 결제_저장(secondOrder, 1_000L, 200L, 800L);
        Refund refund = 환불_저장(firstPayment);

        // when
        // then
        assertThatThrownBy(() -> pointService.restoreUsedPoints(secondPayment, refund, 200L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void 사용포인트복구_복구금액이음수이면_INVALID_POINT_TRANSACTION_AMOUNT가발생한다() {
        // given
        User user = 회원_저장(0L);
        Order order = 주문_저장(user, 1_000L, 200L);
        Payment payment = 결제_저장(order, 1_000L, 200L, 800L);
        Refund refund = 환불_저장(payment);

        // when
        // then
        assertThatThrownBy(() -> pointService.restoreUsedPoints(payment, refund, -1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_POINT_TRANSACTION_AMOUNT);
    }

    @Test
    void 적립포인트회수_잔액이충분하면_전액회수하고_EARN_CANCEL원장을생성한다() {
        // given
        User user = 회원_저장(100L);
        Order order = 주문_저장(user, 1000L, 0L);
        Payment payment = 결제_저장(order, 1000L, 0L, 1000L);
        Refund refund = 환불_저장(payment);

        // when
        pointService.reserveEarnedPointRecoveryFromBalance(payment, refund, 10L);

        // then
        User foundUser = userRepository.findById(user.getId()).orElseThrow();
        List<PointTransaction> pointTransactions = pointTransactionRepository.findAll();

        assertThat(foundUser.getPointBalance()).isEqualTo(90L);
        assertThat(pointTransactions).hasSize(1);
        assertThat(pointTransactions.get(0).getType()).isEqualTo(PointTransactionType.EARN_RECOVERY_RESERVE);
        assertThat(pointTransactions.get(0).getAmount()).isEqualTo(10L);
    }

    @Test
    void 적립포인트회수_잔액이부족하면_가능한만큼만회수하고_부족분을반환한다() {
        // given
        User user = 회원_저장(3L);
        Order order = 주문_저장(user, 1000L, 0L);
        Payment payment = 결제_저장(order, 1000L, 0L, 1000L);
        Refund refund = 환불_저장(payment);

        // when
        // then
        assertThatThrownBy(() -> pointService.reserveEarnedPointRecoveryFromBalance(payment, refund, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_POINT);

        User foundUser = userRepository.findById(user.getId()).orElseThrow();

        assertThat(foundUser.getPointBalance()).isEqualTo(3L);
        assertThat(pointTransactionRepository.count()).isZero();
    }

    @Test
    void 적립포인트회수_회수가능잔액이0이어도_멱등원장을남긴다() {
        // given
        User user = 회원_저장(0L);
        Order order = 주문_저장(user, 1000L, 0L);
        Payment payment = 결제_저장(order, 1000L, 0L, 1000L);
        Refund refund = 환불_저장(payment);

        // when
        pointService.reserveEarnedPointRecoveryFromBalance(payment, refund, 0L);

        // then
        User foundUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(foundUser.getPointBalance()).isZero();
        assertThat(pointTransactionRepository.count()).isZero();
    }

    @Test
    void 예약회수해제_예약원장이있으면_잔액을복구하고_RELEASE원장을생성한다() {
        // given
        User user = 회원_저장(100L);
        Order order = 주문_저장(user, 1_000L, 0L);
        Payment payment = 결제_저장(order, 1_000L, 0L, 1_000L);
        Refund refund = 환불_저장(payment, 10L);

        pointService.reserveEarnedPointRecoveryFromBalance(payment, refund, 10L);

        // when
        pointService.releaseReservedEarnedPointRecovery(payment, refund);

        // then
        User foundUser = userRepository.findById(user.getId()).orElseThrow();
        List<PointTransaction> pointTransactions = pointTransactionRepository.findAll();

        assertThat(foundUser.getPointBalance()).isEqualTo(100L);
        assertThat(pointTransactions)
                .extracting(PointTransaction::getType)
                .containsExactlyInAnyOrder(
                        PointTransactionType.EARN_RECOVERY_RESERVE,
                        PointTransactionType.EARN_RECOVERY_RELEASE
                );
    }

    @Test
    void 예약회수해제_같은환불을두번해제해도_한번만복구한다() {
        // given
        User user = 회원_저장(100L);
        Order order = 주문_저장(user, 1_000L, 0L);
        Payment payment = 결제_저장(order, 1_000L, 0L, 1_000L);
        Refund refund = 환불_저장(payment, 10L);

        pointService.reserveEarnedPointRecoveryFromBalance(payment, refund, 10L);

        // when
        pointService.releaseReservedEarnedPointRecovery(payment, refund);
        pointService.releaseReservedEarnedPointRecovery(payment, refund);

        // then
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isEqualTo(100L);
        assertThat(pointTransactionRepository.count()).isEqualTo(2);
    }

    @Test
    void 예약회수해제_회수금액이0원이면_예약원장이없어도_잔액과원장을변경하지않는다() {
        // given
        User user = 회원_저장(100L);
        Order order = 주문_저장(user, 1_000L, 0L);
        Payment payment = 결제_저장(order, 1_000L, 0L, 1_000L);
        Refund refund = 환불_저장(payment, 0L);

        // when
        pointService.releaseReservedEarnedPointRecovery(payment, refund);

        // then
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isEqualTo(100L);
        assertThat(pointTransactionRepository.count()).isZero();
    }

    @Test
    void 예약회수해제_예약원장이없으면_POINT_ERROR_EXCEPTION이발생한다() {
        // given
        User user = 회원_저장(100L);
        Order order = 주문_저장(user, 1_000L, 0L);
        Payment payment = 결제_저장(order, 1_000L, 0L, 1_000L);
        Refund refund = 환불_저장(payment, 10L);

        // when
        // then
        assertThatThrownBy(() -> pointService.releaseReservedEarnedPointRecovery(payment, refund))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_ERROR_EXCEPTION);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isEqualTo(100L);
    }

    @Test
    void 예약회수해제_환불의결제와요청결제가다르면_VALIDATION_FAILED가발생한다() {
        // given
        User user = 회원_저장(100L);
        Order firstOrder = 주문_저장(user, 1_000L, 0L);
        Order secondOrder = 주문_저장(user, 1_000L, 0L);
        Payment firstPayment = 결제_저장(firstOrder, 1_000L, 0L, 1_000L);
        Payment secondPayment = 결제_저장(secondOrder, 1_000L, 0L, 1_000L);
        Refund refund = 환불_저장(firstPayment, 10L);

        // when
        // then
        assertThatThrownBy(() -> pointService.releaseReservedEarnedPointRecovery(secondPayment, refund))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void 주문취소포인트복구_복구금액이0이면_예약원장이없어도원장을만들지않는다() {
        // given
        User user = 회원_저장(1_000L);
        Order order = 주문_저장(user, 1_000L, 500L);
        Payment payment = 결제_저장(order, 1_000L, 500L, 500L);

        // when
        pointService.restoreReservedPointsForOrderCancel(payment, 0L, List.of("1:1:1"));

        // then
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isEqualTo(1_000L);
        assertThat(pointTransactionRepository.count()).isZero();
    }

    @Test
    void 주문취소포인트복구_요청값이없거나금액이음수이면_예외가발생한다() {
        // given
        User user = 회원_저장(1_000L);
        Order order = 주문_저장(user, 1_000L, 500L);
        Payment payment = 결제_저장(order, 1_000L, 500L, 500L);

        // when
        // then
        assertThatThrownBy(() -> pointService.restoreReservedPointsForOrderCancel(payment, null, List.of("1:1:1")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
        assertThatThrownBy(() -> pointService.restoreReservedPointsForOrderCancel(payment, -1L, List.of("1:1:1")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_POINT_TRANSACTION_AMOUNT);
        assertThatThrownBy(() -> pointService.restoreReservedPointsForOrderCancel(payment, 1L, List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
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

    private Order 주문_저장(User user, Long totalAmount, Long usedPointAmount) {
        return orderRepository.save(Order.create(user, uniqueOrderNumber(), totalAmount, usedPointAmount));
    }

    private Payment 결제_저장(Order order, Long totalAmount, Long usedPointAmount, Long pgAmount) {
        return paymentRepository.save(Payment.createPending(order, totalAmount, usedPointAmount, pgAmount, pgAmount / 100));
    }

    private Refund 환불_저장(Payment payment) {
        return 환불_저장(payment, 0L);
    }

    private Refund 환불_저장(Payment payment, Long recoveredFromBalance) {
        return refundTestRepository.save(Refund.createRefund(
                "REFUND-" + UUID.randomUUID(),
                UUID.randomUUID().toString().replace("-", ""),
                payment.getOrder(),
                payment,
                "테스트 환불",
                payment.getTotalAmount(),
                payment.getUsedPointAmount(),
                payment.getPgAmount(),
                payment.getUsedPointAmount(),
                payment.getPgAmount(),
                recoveredFromBalance,
                0L,
                recoveredFromBalance,
                0L
        ));
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    private String uniqueOrderNumber() {
        return "ORDER-" + UUID.randomUUID();
    }
}
