package com.teamec2.paymentsystem.infra.portone.webhook;

import com.teamec2.paymentsystem.infra.portone.config.PortoneProperties;
import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.Webhook;
import io.portone.sdk.server.webhook.WebhookTransactionPaid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortoneWebhookVerifierTest {

    private static final String WEBHOOK_SECRET = "whsec_MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";
    private static final String WEBHOOK_ID = "webhook-1";
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

    PortoneWebhookVerifier verifier;

    @BeforeEach
    void setUp() {
        PortoneProperties properties = new PortoneProperties(
                "https://api.portone.io",
                "test-api-secret",
                "test-store-id",
                "test-channel-key",
                WEBHOOK_SECRET
        );
        verifier = new PortoneWebhookVerifier(properties);
    }

    @Test
    void 웹훅검증_유효한서명이면_PortOne웹훅객체를반환한다() throws Exception {
        // given
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = signature(WEBHOOK_ID, timestamp, RAW_BODY);

        // when
        Webhook webhook = verifier.verify(RAW_BODY, WEBHOOK_ID, signature, timestamp);

        // then
        assertThat(webhook).isInstanceOf(WebhookTransactionPaid.class);
    }

    @Test
    void 웹훅검증_서명이틀리면_WebhookVerificationException이발생한다() {
        // given
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        // when
        // then
        assertThatThrownBy(() -> verifier.verify(RAW_BODY, WEBHOOK_ID, "v1,invalid-signature", timestamp))
                .isInstanceOf(WebhookVerificationException.class);
    }

    private String signature(String webhookId, String timestamp, String rawBody) throws Exception {
        byte[] secret = Base64.getDecoder().decode(WEBHOOK_SECRET.substring("whsec_".length()));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        byte[] signature = mac.doFinal(
                (webhookId + "." + timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8)
        );

        return "v1," + Base64.getEncoder().encodeToString(signature);
    }
}
