package com.teamec2.paymentsystem.infra.portone.webhook.controller;

import com.teamec2.paymentsystem.global.exception.ErrorCode;
import com.teamec2.paymentsystem.infra.portone.webhook.PortoneWebhookVerifier;
import com.teamec2.paymentsystem.infra.portone.webhook.dto.PortoneWebhookReceiveResponse;
import com.teamec2.paymentsystem.infra.portone.webhook.service.PortoneWebhookEventService;
import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PortoneWebhookControllerTest {

    private static final int BODY_STATUS = 200;
    private static final String WEBHOOK_ID = "webhook-1";
    private static final String WEBHOOK_TIMESTAMP = "1716975300";
    private static final String WEBHOOK_SIGNATURE = "v1,test-signature";
    private static final String RAW_BODY = """
            {
              "type": "Transaction.Paid",
              "timestamp": "2026-05-29T09:35:00.000Z",
              "data": {
                "paymentId": "pay_123",
                "storeId": "store-123",
                "transactionId": "transaction-123"
              }
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PortoneWebhookVerifier portoneWebhookVerifier;

    @MockitoBean
    PortoneWebhookEventService portoneWebhookEventService;

    @BeforeEach
    void setUp() {
        reset(portoneWebhookVerifier, portoneWebhookEventService);
    }

    @Test
    void PortOne웹훅_정상수신하면_200과수신결과를반환한다() throws Exception {
        // given
        Webhook webhook = mock(Webhook.class);
        when(portoneWebhookVerifier.verify(
                RAW_BODY,
                WEBHOOK_ID,
                WEBHOOK_SIGNATURE,
                WEBHOOK_TIMESTAMP
        )).thenReturn(webhook);
        when(portoneWebhookEventService.receive(WEBHOOK_ID, webhook, RAW_BODY))
                .thenReturn(PortoneWebhookReceiveResponse.received("pay_123"));

        // when
        // then
        mockMvc.perform(portoneWebhookRequest(WEBHOOK_ID, WEBHOOK_TIMESTAMP, WEBHOOK_SIGNATURE, RAW_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.message").value("요청이 성공했습니다."))
                .andExpect(jsonPath("$.data.received").value(true))
                .andExpect(jsonPath("$.data.processed").value(false))
                .andExpect(jsonPath("$.data.portonePaymentId").value("pay_123"))
                .andExpect(jsonPath("$.data.reason").value("RECEIVED"));

        verify(portoneWebhookVerifier).verify(RAW_BODY, WEBHOOK_ID, WEBHOOK_SIGNATURE, WEBHOOK_TIMESTAMP);
        verify(portoneWebhookEventService).receive(WEBHOOK_ID, webhook, RAW_BODY);
    }

    @Test
    void PortOne웹훅_필수헤더가없으면_WEBHOOK_SIGNATURE_INVALID를반환한다() throws Exception {
        // given

        // when
        // then
        mockMvc.perform(portoneWebhookRequest(null, WEBHOOK_TIMESTAMP, WEBHOOK_SIGNATURE, RAW_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.WEBHOOK_SIGNATURE_INVALID.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.WEBHOOK_SIGNATURE_INVALID.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(portoneWebhookRequest(WEBHOOK_ID, null, WEBHOOK_SIGNATURE, RAW_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.WEBHOOK_SIGNATURE_INVALID.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.WEBHOOK_SIGNATURE_INVALID.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(portoneWebhookRequest(WEBHOOK_ID, WEBHOOK_TIMESTAMP, null, RAW_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.WEBHOOK_SIGNATURE_INVALID.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.WEBHOOK_SIGNATURE_INVALID.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(portoneWebhookVerifier, portoneWebhookEventService);
    }

    @Test
    void PortOne웹훅_본문이없으면_WEBHOOK_PAYLOAD_INVALID를반환한다() throws Exception {
        // given

        // when
        // then
        mockMvc.perform(portoneWebhookRequest(WEBHOOK_ID, WEBHOOK_TIMESTAMP, WEBHOOK_SIGNATURE, "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.WEBHOOK_PAYLOAD_INVALID.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.WEBHOOK_PAYLOAD_INVALID.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(portoneWebhookVerifier, portoneWebhookEventService);
    }

    @Test
    void PortOne웹훅_서명검증실패면_WEBHOOK_SIGNATURE_INVALID를반환한다() throws Exception {
        // given
        when(portoneWebhookVerifier.verify(
                RAW_BODY,
                WEBHOOK_ID,
                WEBHOOK_SIGNATURE,
                WEBHOOK_TIMESTAMP
        )).thenThrow(new WebhookVerificationException("invalid signature", null));

        // when
        // then
        mockMvc.perform(portoneWebhookRequest(WEBHOOK_ID, WEBHOOK_TIMESTAMP, WEBHOOK_SIGNATURE, RAW_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.WEBHOOK_SIGNATURE_INVALID.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.WEBHOOK_SIGNATURE_INVALID.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(portoneWebhookVerifier).verify(RAW_BODY, WEBHOOK_ID, WEBHOOK_SIGNATURE, WEBHOOK_TIMESTAMP);
        verifyNoInteractions(portoneWebhookEventService);
    }

    @Test
    void PortOne웹훅_본문파싱실패면_WEBHOOK_PAYLOAD_INVALID를반환한다() throws Exception {
        // given
        when(portoneWebhookVerifier.verify(
                RAW_BODY,
                WEBHOOK_ID,
                WEBHOOK_SIGNATURE,
                WEBHOOK_TIMESTAMP
        )).thenThrow(new IllegalArgumentException("invalid payload"));

        // when
        // then
        mockMvc.perform(portoneWebhookRequest(WEBHOOK_ID, WEBHOOK_TIMESTAMP, WEBHOOK_SIGNATURE, RAW_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.WEBHOOK_PAYLOAD_INVALID.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.WEBHOOK_PAYLOAD_INVALID.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(portoneWebhookVerifier).verify(RAW_BODY, WEBHOOK_ID, WEBHOOK_SIGNATURE, WEBHOOK_TIMESTAMP);
        verifyNoInteractions(portoneWebhookEventService);
    }

    private MockHttpServletRequestBuilder portoneWebhookRequest(
            String webhookId,
            String webhookTimestamp,
            String webhookSignature,
            String rawBody
    ) {
        MockHttpServletRequestBuilder request = post("/api/webhooks/portone")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rawBody);

        if (webhookId != null) {
            request.header("webhook-id", webhookId);
        }
        if (webhookTimestamp != null) {
            request.header("webhook-timestamp", webhookTimestamp);
        }
        if (webhookSignature != null) {
            request.header("webhook-signature", webhookSignature);
        }

        return request;
    }
}
