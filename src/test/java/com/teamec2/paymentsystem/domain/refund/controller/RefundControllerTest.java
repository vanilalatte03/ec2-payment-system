package com.teamec2.paymentsystem.domain.refund.controller;

import com.teamec2.paymentsystem.domain.refund.dto.FullRefundRequest;
import com.teamec2.paymentsystem.domain.refund.dto.PartialRefundRequest;
import com.teamec2.paymentsystem.domain.refund.dto.RefundItemResponse;
import com.teamec2.paymentsystem.domain.refund.dto.RefundResponse;
import com.teamec2.paymentsystem.domain.refund.enums.RefundStatus;
import com.teamec2.paymentsystem.domain.refund.service.RefundService;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.domain.user.repository.UserRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import com.teamec2.paymentsystem.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RefundControllerTest {

    private static final int BODY_STATUS = 200;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    RefundService refundService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        reset(refundService);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
        reset(refundService);
    }

    @Test
    void 부분환불요청_성공하면_201과환불응답을반환한다() throws Exception {
        // given
        User user = 회원_저장();
        RefundResponse response = 환불응답(100L, "partial refund", RefundStatus.PROCESSING);
        when(refundService.requestPartialRefund(
                eq(user.getId()),
                eq(10L),
                eq("refund-key-1"),
                any(PartialRefundRequest.class)
        )).thenReturn(response);

        // when
        // then
        mockMvc.perform(post("/api/orders/{orderId}/refunds", 10L)
                        .header("Authorization", "Bearer " + accessToken(user))
                        .header("Idempotency-Key", "refund-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "partial refund",
                                  "items": [
                                    {
                                      "orderItemId": 20,
                                      "quantity": 1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.message").value("요청이 성공했습니다."))
                .andExpect(jsonPath("$.data.refundId").value(100L))
                .andExpect(jsonPath("$.data.actualRefundAmount").value(3_000L))
                .andExpect(jsonPath("$.data.pointRefundAmount").value(0L))
                .andExpect(jsonPath("$.data.pgRefundAmount").value(3_000L))
                .andExpect(jsonPath("$.data.reason").value("partial refund"))
                .andExpect(jsonPath("$.data.refundStatus").value(RefundStatus.PROCESSING.name()))
                .andExpect(jsonPath("$.data.items[0].orderItemId").value(20L))
                .andExpect(jsonPath("$.data.items[0].refundQuantity").value(1));
    }

    @Test
    void 전체환불요청_성공하면_201과환불응답을반환한다() throws Exception {
        // given
        User user = 회원_저장();
        RefundResponse response = 환불응답(101L, "full refund", RefundStatus.PROCESSING);
        when(refundService.requestFullRefund(
                eq(user.getId()),
                eq(30L),
                eq("refund-key-2"),
                any(FullRefundRequest.class)
        )).thenReturn(response);

        // when
        // then
        mockMvc.perform(post("/api/payments/{paymentId}/refunds", 30L)
                        .header("Authorization", "Bearer " + accessToken(user))
                        .header("Idempotency-Key", "refund-key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "full refund"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.data.refundId").value(101L))
                .andExpect(jsonPath("$.data.reason").value("full refund"))
                .andExpect(jsonPath("$.data.refundStatus").value(RefundStatus.PROCESSING.name()));
    }

    @Test
    void 부분환불요청_토큰이없으면_UNAUTHORIZED를반환하고서비스를호출하지않는다() throws Exception {
        // when
        // then
        mockMvc.perform(post("/api/orders/{orderId}/refunds", 10L)
                        .header("Idempotency-Key", "refund-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "partial refund",
                                  "items": [
                                    {
                                      "orderItemId": 20,
                                      "quantity": 1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(refundService);
    }

    @Test
    void 부분환불요청_멱등키헤더가없으면_MISSING_REQUIRED_FIELD를반환하고서비스를호출하지않는다() throws Exception {
        // given
        User user = 회원_저장();

        // when
        // then
        mockMvc.perform(post("/api/orders/{orderId}/refunds", 10L)
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "partial refund",
                                  "items": [
                                    {
                                      "orderItemId": 20,
                                      "quantity": 1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().is(ErrorCode.MISSING_REQUIRED_FIELD.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.MISSING_REQUIRED_FIELD.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.MISSING_REQUIRED_FIELD.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(refundService);
    }

    @Test
    void 부분환불요청_같은주문상품ID가중복되면_DUPLICATE_REQUEST를반환한다() throws Exception {
        // given
        User user = 회원_저장();
        when(refundService.requestPartialRefund(
                eq(user.getId()),
                eq(10L),
                eq("refund-key-1"),
                any(PartialRefundRequest.class)
        )).thenThrow(new BusinessException(ErrorCode.DUPLICATE_REQUEST));

        // when
        // then
        mockMvc.perform(post("/api/orders/{orderId}/refunds", 10L)
                        .header("Authorization", "Bearer " + accessToken(user))
                        .header("Idempotency-Key", "refund-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "partial refund",
                                  "items": [
                                    {
                                      "orderItemId": 20,
                                      "quantity": 1
                                    },
                                    {
                                      "orderItemId": 20,
                                      "quantity": 1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().is(ErrorCode.DUPLICATE_REQUEST.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_REQUEST.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.DUPLICATE_REQUEST.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 부분환불요청_본문검증실패면_VALIDATION_FAILED를반환하고서비스를호출하지않는다() throws Exception {
        // given
        User user = 회원_저장();

        // when
        // then
        mockMvc.perform(post("/api/orders/{orderId}/refunds", 10L)
                        .header("Authorization", "Bearer " + accessToken(user))
                        .header("Idempotency-Key", "refund-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "",
                                  "items": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.data").isArray());

        verifyNoInteractions(refundService);
    }

    private User 회원_저장() {
        return userRepository.save(User.create(
                uniqueEmail(),
                "Password123!",
                "홍길동",
                "010-1234-5678"
        ));
    }

    private String accessToken(User user) {
        return jwtTokenProvider.createAccessToken(user.getId());
    }

    private RefundResponse 환불응답(Long refundId, String reason, RefundStatus status) {
        return new RefundResponse(
                refundId,
                3_000L,
                0L,
                3_000L,
                reason,
                LocalDateTime.of(2026, 6, 1, 12, 30),
                null,
                status,
                List.of(new RefundItemResponse(
                        1L,
                        20L,
                        "후드 집업",
                        1,
                        3_000L,
                        3_000L,
                        3_000L,
                        0L,
                        3_000L
                ))
        );
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }
}
