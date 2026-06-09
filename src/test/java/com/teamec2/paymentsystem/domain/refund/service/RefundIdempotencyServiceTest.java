package com.teamec2.paymentsystem.domain.refund.service;

import com.teamec2.paymentsystem.domain.refund.dto.FullRefundRequest;
import com.teamec2.paymentsystem.domain.refund.dto.PartialRefundRequest;
import com.teamec2.paymentsystem.domain.refund.dto.RefundItemRequest;
import com.teamec2.paymentsystem.domain.refund.entity.Refund;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefundIdempotencyServiceTest {

    RefundIdempotencyService refundIdempotencyService;

    @BeforeEach
    void setUp() {
        refundIdempotencyService = new RefundIdempotencyService();
    }

    @Test
    void 멱등키검증_비어있으면_MISSING_REQUIRED_FIELD가발생한다() {
        // when
        // then
        assertThatThrownBy(() -> refundIdempotencyService.validateIdempotencyKey(" "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
    }

    @Test
    void 부분환불요청해시_상품순서가달라도_같은요청이면_같은해시를생성한다() {
        // given
        PartialRefundRequest firstRequest = new PartialRefundRequest(
                "partial refund",
                List.of(
                        new RefundItemRequest(2L, 1),
                        new RefundItemRequest(1L, 3)
                )
        );
        PartialRefundRequest secondRequest = new PartialRefundRequest(
                "partial refund",
                List.of(
                        new RefundItemRequest(1L, 3),
                        new RefundItemRequest(2L, 1)
                )
        );

        // when
        String firstHash = refundIdempotencyService.createPartialRefundRequestHash(firstRequest);
        String secondHash = refundIdempotencyService.createPartialRefundRequestHash(secondRequest);

        // then
        assertThat(firstHash).isEqualTo(secondHash);
        assertThat(firstHash).hasSize(64);
    }

    @Test
    void 부분환불요청해시_같은상품이어도수량이다르면_다른해시를생성한다() {
        // given
        PartialRefundRequest firstRequest = new PartialRefundRequest(
                "partial refund",
                List.of(new RefundItemRequest(1L, 1))
        );
        PartialRefundRequest secondRequest = new PartialRefundRequest(
                "partial refund",
                List.of(new RefundItemRequest(1L, 2))
        );

        // when
        String firstHash = refundIdempotencyService.createPartialRefundRequestHash(firstRequest);
        String secondHash = refundIdempotencyService.createPartialRefundRequestHash(secondRequest);

        // then
        assertThat(firstHash).isNotEqualTo(secondHash);
    }

    @Test
    void 전체환불요청해시_사유가같으면_같은해시를생성한다() {
        // given
        FullRefundRequest firstRequest = new FullRefundRequest("full refund");
        FullRefundRequest secondRequest = new FullRefundRequest("full refund");

        // when
        String firstHash = refundIdempotencyService.createFullRefundRequestHash(firstRequest);
        String secondHash = refundIdempotencyService.createFullRefundRequestHash(secondRequest);

        // then
        assertThat(firstHash).isEqualTo(secondHash);
        assertThat(firstHash).hasSize(64);
    }

    @Test
    void 같은멱등요청검증_기존요청해시와다르면_CONFLICT가발생한다() {
        // given
        Refund refund = mock(Refund.class);
        when(refund.getRequestHash()).thenReturn("old-hash");

        // when
        // then
        assertThatThrownBy(() -> refundIdempotencyService.validateSameIdempotentRequest(
                refund,
                "new-hash"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }
}
