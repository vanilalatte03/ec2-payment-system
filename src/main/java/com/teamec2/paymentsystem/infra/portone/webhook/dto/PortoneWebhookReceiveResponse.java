package com.teamec2.paymentsystem.infra.portone.webhook.dto;

public record PortoneWebhookReceiveResponse(
        boolean received,
        boolean processed,
        String portonePaymentId,
        String reason
) {

    public static PortoneWebhookReceiveResponse received(String portonePaymentId) {
        return new PortoneWebhookReceiveResponse(
                true,
                false,
                portonePaymentId,
                "RECEIVED"
        );
    }

    public static PortoneWebhookReceiveResponse duplicated() {
        return new PortoneWebhookReceiveResponse(
                true,
                false,
                null,
                "DUPLICATE_WEBHOOK_ID"
        );
    }

    public static PortoneWebhookReceiveResponse ignored(String reason) {
        return new PortoneWebhookReceiveResponse(
                true,
                false,
                null,
                reason
        );
    }
}
