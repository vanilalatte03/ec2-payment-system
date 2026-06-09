package com.teamec2.paymentsystem.domain.point.entity;

import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.point.enums.PointTransactionType;
import com.teamec2.paymentsystem.domain.refund.entity.Refund;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class PointTransactionTest {

    User user;
    Payment payment;
    Refund refund;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        payment = mock(Payment.class);
        refund = mock(Refund.class);

        lenient().when(user.getId()).thenReturn(1L);
        lenient().when(payment.getId()).thenReturn(10L);
        lenient().when(refund.getId()).thenReturn(20L);
    }

    @Test
    void 결제포인트거래_생성하면_PAYMENT멱등키를저장한다() {
        // given

        // when
        PointTransaction transaction = PointTransaction.createForPayment(
                user,
                payment,
                PointTransactionType.EARN,
                100L
        );

        // then
        assertThat(transaction.getUser()).isSameAs(user);
        assertThat(transaction.getPayment()).isSameAs(payment);
        assertThat(transaction.getRefund()).isNull();
        assertThat(transaction.getType()).isEqualTo(PointTransactionType.EARN);
        assertThat(transaction.getAmount()).isEqualTo(100L);
        assertThat(transaction.getIdempotencyKey()).isEqualTo("PAYMENT:10:EARN");
    }

    @Test
    void 결제전주문취소포인트거래_주문상품키순서가달라도_같은멱등키를생성한다() {
        // given

        // when
        PointTransaction firstTransaction = PointTransaction.createForPaymentOrderCancel(
                user,
                payment,
                PointTransactionType.USE_CANCEL,
                100L,
                List.of("2:3:1", "1:2:1")
        );
        PointTransaction secondTransaction = PointTransaction.createForPaymentOrderCancel(
                user,
                payment,
                PointTransactionType.USE_CANCEL,
                100L,
                List.of("1:2:1", "2:3:1")
        );

        // then
        assertThat(firstTransaction.getIdempotencyKey()).isEqualTo(secondTransaction.getIdempotencyKey());
        assertThat(firstTransaction.getIdempotencyKey()).startsWith("PAYMENT:10:USE_CANCEL:");
    }

    @Test
    void 환불포인트거래_생성하면_REFUND멱등키를저장한다() {
        // given

        // when
        PointTransaction transaction = PointTransaction.createForRefund(
                user,
                payment,
                refund,
                PointTransactionType.USE_RESTORE,
                100L
        );

        // then
        assertThat(transaction.getUser()).isSameAs(user);
        assertThat(transaction.getPayment()).isSameAs(payment);
        assertThat(transaction.getRefund()).isSameAs(refund);
        assertThat(transaction.getType()).isEqualTo(PointTransactionType.USE_RESTORE);
        assertThat(transaction.getIdempotencyKey()).isEqualTo("REFUND:20:USE_RESTORE");
    }

    @Test
    void 회원가입보너스거래_생성하면_SIGNUP_BONUS멱등키를저장한다() {
        // given

        // when
        PointTransaction transaction = PointTransaction.createForSignupBonus(user, 1_000L);

        // then
        assertThat(transaction.getUser()).isSameAs(user);
        assertThat(transaction.getPayment()).isNull();
        assertThat(transaction.getRefund()).isNull();
        assertThat(transaction.getType()).isEqualTo(PointTransactionType.SIGNUP_BONUS);
        assertThat(transaction.getIdempotencyKey()).isEqualTo("SIGNUP_BONUS:1");
    }

    @Test
    void 사용예약원장_확정하면_USE타입과USE멱등키로변경한다() {
        // given
        PointTransaction transaction = PointTransaction.createForPayment(
                user,
                payment,
                PointTransactionType.USE_RESERVE,
                100L
        );

        // when
        transaction.confirmUse();

        // then
        assertThat(transaction.getType()).isEqualTo(PointTransactionType.USE);
        assertThat(transaction.getIdempotencyKey()).isEqualTo("PAYMENT:10:USE");
    }

    @Test
    void 결제포인트거래_환불타입이면_INVALID_POINT_TRANSACTION_TYPE가발생한다() {
        // when
        // then
        assertThatThrownBy(() -> PointTransaction.createForPayment(
                user,
                payment,
                PointTransactionType.USE_RESTORE,
                100L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_POINT_TRANSACTION_TYPE);
    }

    @Test
    void 포인트거래_0원거래는_EARN_CANCEL만허용한다() {
        // when
        PointTransaction transaction = PointTransaction.createForRefund(
                user,
                payment,
                refund,
                PointTransactionType.EARN_CANCEL,
                0L
        );

        // then
        assertThat(transaction.getAmount()).isZero();
        assertThatThrownBy(() -> PointTransaction.createForRefund(
                user,
                payment,
                refund,
                PointTransactionType.USE_RESTORE,
                0L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_POINT_TRANSACTION_AMOUNT);
    }

    @Test
    void 사용예약이아닌원장을확정하려면_INVALID_POINT_TRANSACTION_TYPE가발생한다() {
        // given
        PointTransaction transaction = PointTransaction.createForPayment(
                user,
                payment,
                PointTransactionType.EARN,
                100L
        );

        // when
        // then
        assertThatThrownBy(transaction::confirmUse)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_POINT_TRANSACTION_TYPE);
    }

    @Test
    void 포인트거래_필수값이없으면_MISSING_REQUIRED_FIELD가발생한다() {
        // when
        // then
        assertThatThrownBy(() -> PointTransaction.createForPayment(
                null,
                payment,
                PointTransactionType.EARN,
                100L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
        assertThatThrownBy(() -> PointTransaction.createForPayment(
                user,
                null,
                PointTransactionType.EARN,
                100L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
        assertThatThrownBy(() -> PointTransaction.paymentIdempotencyKey(payment, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
    }

    @Test
    void 포인트거래_금액이음수이면_INVALID_POINT_TRANSACTION_AMOUNT가발생한다() {
        // when
        // then
        assertThatThrownBy(() -> PointTransaction.createForPayment(
                user,
                payment,
                PointTransactionType.EARN,
                -1L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_POINT_TRANSACTION_AMOUNT);
    }
}
