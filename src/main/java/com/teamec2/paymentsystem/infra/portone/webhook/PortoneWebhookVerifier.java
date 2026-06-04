package com.teamec2.paymentsystem.infra.portone.webhook;

import com.teamec2.paymentsystem.infra.portone.config.PortoneProperties;
import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.Webhook;
import io.portone.sdk.server.webhook.WebhookVerifier;
import org.springframework.stereotype.Component;

@Component
public class PortoneWebhookVerifier {

    private final WebhookVerifier webhookVerifier;

    public PortoneWebhookVerifier(PortoneProperties properties) {
        this.webhookVerifier = new WebhookVerifier(properties.webhookSecret());
    }

    public Webhook verify(
            String rawBody,
            String webhookId,
            String webhookSignature,
            String webhookTimestamp
    ) throws WebhookVerificationException {
        return webhookVerifier.verify(
                rawBody,
                webhookId,
                webhookSignature,
                webhookTimestamp
        );
    }
}
