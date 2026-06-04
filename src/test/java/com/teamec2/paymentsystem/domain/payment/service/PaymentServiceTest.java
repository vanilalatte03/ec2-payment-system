package com.teamec2.paymentsystem.domain.payment.service;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderStatus;
import com.teamec2.paymentsystem.domain.order.repository.OrderRepository;
import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentRequest;
import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentResponse;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentStatus;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentType;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGateway;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGatewayResponse;
import com.teamec2.paymentsystem.domain.payment.repository.PaymentRepository;
import com.teamec2.paymentsystem.domain.point.entity.PointTransaction;
import com.teamec2.paymentsystem.domain.point.enums.PointTransactionType;
import com.teamec2.paymentsystem.domain.point.repository.PointTransactionRepository;
import com.teamec2.paymentsystem.domain.point.service.PointService;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.domain.user.repository.UserRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
class PaymentServiceTest {

    @Autowired
    PaymentService paymentService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    PointService pointService;

    @Autowired
    PointTransactionRepository pointTransactionRepository;

    @Autowired
    TestPaymentGateway testPaymentGateway;

    @BeforeEach
    void setUp() {
        clearDatabase();
        testPaymentGateway.reset();
    }

    @AfterEach
    void tearDown() {
        clearDatabase();
        testPaymentGateway.reset();
    }

    private void clearDatabase() {
        pointTransactionRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 결제확정_PG결제성공이면_주문과결제를완료한다() {
        // given
        User user = 회원_저장(10000L);
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        pointService.reserveUsedPoints(payment);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);
        testPaymentGateway.success(payment.getPortonePaymentId(), 800L, approvedAt);

        // when
        ConfirmPaymentResponse response = paymentService.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        );

        // then
        Order foundOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(foundPayment.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(testPaymentGateway.getCallCount()).isEqualTo(1);

        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.paymentId()).isEqualTo(payment.getId());
        assertThat(response.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.paymentType()).isEqualTo(PaymentType.POINT_CARD);
        assertThat(response.pgAmount()).isEqualTo(800L);
        assertThat(response.approvedAt()).isEqualTo(approvedAt.atOffset(ZoneOffset.ofHours(9)));
    }

    @Test
    void 결제확정_성공시_예약포인트를_USE로확정하고_적립원장을생성한다() {
        // given
        User user = 회원_저장(10000L);
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        pointService.reserveUsedPoints(payment);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);
        testPaymentGateway.success(payment.getPortonePaymentId(), 800L, approvedAt);

        // when
        paymentService.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        );

        // then
        User foundUser = userRepository.findById(user.getId()).orElseThrow();
        List<PointTransaction> pointTransactions = pointTransactionRepository.findAll();

        assertThat(foundUser.getPointBalance()).isEqualTo(9808L);
        assertThat(pointTransactions)
                .extracting(PointTransaction::getType, PointTransaction::getAmount, PointTransaction::getIdempotencyKey)
                .containsExactlyInAnyOrder(
                        tuple(PointTransactionType.USE, 200L, "PAYMENT:%d:USE".formatted(payment.getId())),
                        tuple(PointTransactionType.EARN, 8L, "PAYMENT:%d:EARN".formatted(payment.getId()))
                );
    }

    @Test
    void 결제확정_중복호출시_포인트원장을중복생성하지않는다() {
        // given
        User user = 회원_저장(10000L);
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        pointService.reserveUsedPoints(payment);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);
        testPaymentGateway.success(payment.getPortonePaymentId(), 800L, approvedAt);

        // when
        paymentService.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        );
        paymentService.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        );

        // then
        User foundUser = userRepository.findById(user.getId()).orElseThrow();
        List<PointTransaction> pointTransactions = pointTransactionRepository.findAll();

        assertThat(foundUser.getPointBalance()).isEqualTo(9808L);
        assertThat(pointTransactions)
                .extracting(PointTransaction::getType, PointTransaction::getAmount, PointTransaction::getIdempotencyKey)
                .containsExactlyInAnyOrder(
                        tuple(PointTransactionType.USE, 200L, "PAYMENT:%d:USE".formatted(payment.getId())),
                        tuple(PointTransactionType.EARN, 8L, "PAYMENT:%d:EARN".formatted(payment.getId()))
                );
    }

    @Test
    void 결제확정_포인트전액결제이면_PortOne조회없이_완료한다() {
        // given
        User user = 회원_저장(1000L);
        Order order = 주문_저장(user, 1000L, 1000L);
        Payment payment = 결제_저장(order, 1000L, 1000L, 0L);
        pointService.reserveUsedPoints(payment);

        // when
        ConfirmPaymentResponse response = paymentService.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        );

        // then
        Order foundOrder = orderRepository.findById(order.getId()).orElseThrow();
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(testPaymentGateway.getCallCount()).isZero();
        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(foundPayment.getApprovedAt()).isNotNull();
        assertThat(response.paymentType()).isEqualTo(PaymentType.POINT_ONLY);
        assertThat(response.pgAmount()).isZero();
        assertThat(response.cartCleared()).isFalse();
    }

    @Test
    void 결제확정_이미완료된결제이면_상태변경없이_성공응답한다() {
        // given
        User user = 회원_저장();
        Order order = 주문_저장(user, 1000L, 200L);
        order.complete();
        orderRepository.saveAndFlush(order);

        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);
        payment.complete(approvedAt);
        paymentRepository.saveAndFlush(payment);

        // when
        ConfirmPaymentResponse response = paymentService.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        );

        // then
        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(testPaymentGateway.getCallCount()).isZero();
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(foundPayment.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(response.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.approvedAt()).isEqualTo(approvedAt.atOffset(ZoneOffset.ofHours(9)));
    }

    @Test
    void 결제확정_타인주문이면_ORDER_ACCESS_DENIED가발생한다() {
        // given
        User owner = 회원_저장();
        User otherUser = 회원_저장();
        Order order = 주문_저장(owner, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);

        // when
        // then
        assertThatThrownBy(() -> paymentService.confirmPayment(
                otherUser.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_ACCESS_DENIED);

        assertThat(testPaymentGateway.getCallCount()).isZero();
    }

    @Test
    void 결제확정_PortOne결제ID가다르면_PAYMENT_PORTONE_ID_MISMATCH가발생한다() {
        // given
        User user = 회원_저장();
        Order order = 주문_저장(user, 1000L, 200L);
        결제_저장(order, 1000L, 200L, 800L);

        // when
        // then
        assertThatThrownBy(() -> paymentService.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), "pay_mismatch")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_PORTONE_ID_MISMATCH);

        assertThat(testPaymentGateway.getCallCount()).isZero();
    }

    @Test
    void 결제확정_PortOne상태가PAID가아니면_PAYMENT_STATUS_NOT_PAID가발생한다() {
        // given
        User user = 회원_저장();
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        testPaymentGateway.response(
                payment.getPortonePaymentId(),
                "FAILED",
                800L,
                LocalDateTime.of(2026, 6, 1, 12, 30)
        );

        // when
        // then
        assertThatThrownBy(() -> paymentService.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_STATUS_NOT_PAID);

        assertThat(testPaymentGateway.getCallCount()).isEqualTo(1);
    }

    @Test
    void 결제확정_PortOne금액이다르면_PAYMENT_AMOUNT_MISMATCH가발생한다() {
        // given
        User user = 회원_저장();
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        testPaymentGateway.success(payment.getPortonePaymentId(), 700L, LocalDateTime.of(2026, 6, 1, 12, 30));

        // when
        // then
        assertThatThrownBy(() -> paymentService.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        assertThat(testPaymentGateway.getCallCount()).isEqualTo(1);
    }

    @Test
    void 결제확정_PortOne승인시각이없으면_EXTERNAL_API_FAILED가발생한다() {
        // given
        User user = 회원_저장();
        Order order = 주문_저장(user, 1000L, 200L);
        Payment payment = 결제_저장(order, 1000L, 200L, 800L);
        testPaymentGateway.success(payment.getPortonePaymentId(), 800L, null);

        // when
        // then
        assertThatThrownBy(() -> paymentService.confirmPayment(
                user.getId(),
                new ConfirmPaymentRequest(order.getId(), payment.getPortonePaymentId())
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_API_FAILED);

        assertThat(testPaymentGateway.getCallCount()).isEqualTo(1);
    }

    private User 회원_저장() {
        return 회원_저장(0L);
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

    @TestConfiguration
    static class PaymentServiceTestConfig {

        @Bean
        @Primary
        TestPaymentGateway testPaymentGateway() {
            return new TestPaymentGateway();
        }
    }

    static class TestPaymentGateway implements PaymentGateway {

        private PaymentGatewayResponse response;
        private int callCount;

        @Override
        public PaymentGatewayResponse getPayment(String paymentId) {
            callCount++;
            return response;
        }

        void success(String paymentId, Long paidAmount, LocalDateTime approvedAt) {
            response(paymentId, "PAID", paidAmount, approvedAt);
        }

        void response(String paymentId, String status, Long paidAmount, LocalDateTime approvedAt) {
            response = new PaymentGatewayResponse(paymentId, status, paidAmount, approvedAt);
        }

        int getCallCount() {
            return callCount;
        }

        void reset() {
            response = null;
            callCount = 0;
        }
    }
}
