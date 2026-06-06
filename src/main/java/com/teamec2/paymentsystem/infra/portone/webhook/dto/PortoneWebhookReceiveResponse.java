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

    /**
     * 처리 대상 웹훅의 결제 확정까지 완료됐음을 나타내는 응답을 만든다.
     *
     * @param portonePaymentId 확정 처리된 PortOne 결제 ID
     * @return 수신과 처리가 모두 완료된 웹훅 응답
     */
    public static PortoneWebhookReceiveResponse processed(String portonePaymentId) {
        return new PortoneWebhookReceiveResponse(
                true,
                true,
                portonePaymentId,
                "PROCESSED"
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
