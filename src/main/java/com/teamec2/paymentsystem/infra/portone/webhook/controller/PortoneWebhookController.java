package com.teamec2.paymentsystem.infra.portone.webhook.controller;

import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import com.teamec2.paymentsystem.global.response.ApiResponse;
import com.teamec2.paymentsystem.infra.portone.webhook.PortoneWebhookVerifier;
import com.teamec2.paymentsystem.infra.portone.webhook.dto.PortoneWebhookReceiveResponse;
import com.teamec2.paymentsystem.infra.portone.webhook.service.PortoneWebhookEventService;
import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class PortoneWebhookController {

    private final PortoneWebhookVerifier portoneWebhookVerifier;
    private final PortoneWebhookEventService portoneWebhookEventService;

    @PostMapping("/portone")
    public ResponseEntity<ApiResponse<PortoneWebhookReceiveResponse>> receivePortoneWebhook(
            @RequestHeader(name = "webhook-id", required = false) String webhookId,
            @RequestHeader(name = "webhook-timestamp", required = false) String webhookTimestamp,
            @RequestHeader(name = "webhook-signature", required = false) String webhookSignature,
            @RequestBody(required = false) String rawBody
    ) {
        validateRequiredWebhookHeaders(webhookId, webhookTimestamp, webhookSignature);
        if (rawBody == null || rawBody.isBlank()) {
            throw new BusinessException(ErrorCode.WEBHOOK_PAYLOAD_INVALID);
        }

        Webhook webhook;
        try {
            webhook = portoneWebhookVerifier.verify(
                    rawBody,
                    webhookId,
                    webhookSignature,
                    webhookTimestamp
            );

        } catch (WebhookVerificationException e) {
            log.warn("PortOne 웹훅 서명 검증 실패: webhookId={}", webhookId, e);
            throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        } catch (RuntimeException e) {
            log.warn("PortOne 웹훅 본문 파싱 실패: webhookId={}", webhookId, e);
            throw new BusinessException(ErrorCode.WEBHOOK_PAYLOAD_INVALID);
        }

        log.info("PortOne 웹훅 서명 검증 성공: webhookId={}", webhookId);

        PortoneWebhookReceiveResponse response = portoneWebhookEventService.receive(
                webhookId,
                webhook,
                rawBody
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private void validateRequiredWebhookHeaders(
            String webhookId,
            String webhookTimestamp,
            String webhookSignature
    ) {
        if (isBlank(webhookId) || isBlank(webhookTimestamp) || isBlank(webhookSignature)) {
            throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
