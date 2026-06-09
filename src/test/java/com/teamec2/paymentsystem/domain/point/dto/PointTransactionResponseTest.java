package com.teamec2.paymentsystem.domain.point.dto;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.point.entity.PointTransaction;
import com.teamec2.paymentsystem.domain.point.enums.PointTransactionType;
import com.teamec2.paymentsystem.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PointTransactionResponseTest {

    @Test
    void 포인트거래응답_결제거래이면_paymentId를포함한다() {
        // given
        User user = 회원(1L);
        Payment payment = 결제(user, 10L);
        PointTransaction transaction = PointTransaction.createForPayment(
                user,
                payment,
                PointTransactionType.EARN,
                100L
        );
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 1, 12, 0);
        ReflectionTestUtils.setField(transaction, "id", 100L);
        ReflectionTestUtils.setField(transaction, "createdAt", createdAt);

        // when
        PointTransactionResponse response = PointTransactionResponse.from(transaction);

        // then
        assertThat(response.getPointTransactionId()).isEqualTo(100L);
        assertThat(response.getPaymentId()).isEqualTo(10L);
        assertThat(response.getType()).isEqualTo(PointTransactionType.EARN);
        assertThat(response.getAmount()).isEqualTo(100L);
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void 포인트거래응답_계정거래이면_paymentId가null이다() {
        // given
        User user = 회원(1L);
        PointTransaction transaction = PointTransaction.createForSignupBonus(user, 1_000L);

        // when
        PointTransactionResponse response = PointTransactionResponse.from(transaction);

        // then
        assertThat(response.getPaymentId()).isNull();
        assertThat(response.getType()).isEqualTo(PointTransactionType.SIGNUP_BONUS);
        assertThat(response.getAmount()).isEqualTo(1_000L);
    }

    private User 회원(Long userId) {
        User user = User.create("user-" + userId + "@example.com", "Password123!", "홍길동", "010-1234-5678");
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Payment 결제(User user, Long paymentId) {
        Order order = Order.create(user, "ORDER-" + paymentId, 1_000L, 200L);
        ReflectionTestUtils.setField(order, "id", paymentId);
        Payment payment = Payment.createPending(order, 1_000L, 200L, 800L, 8L);
        ReflectionTestUtils.setField(payment, "id", paymentId);
        return payment;
    }
}
