package com.teamec2.paymentsystem.infra.portone.webhook.entity;

import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.refund.entity.Refund;
import com.teamec2.paymentsystem.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "webhook_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_webhook_events_webhook_id", columnNames = "webhook_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortoneWebhookEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id")
    private Refund refund;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "webhook_id", nullable = false, length = 200)
    private String webhookId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WebhookEventStatus status;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "portone_payment_id", length = 100)
    private String portonePaymentId;

    @Column(name = "portone_cancellation_id", length = 100)
    private String portoneCancellationId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Lob
    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    /**
     * 처리 대상 웹훅 이벤트를 수신 상태로 생성한다.
     *
     * @param webhookId PortOne 웹훅 헤더의 메시지 ID
     * @param type 웹훅 이벤트 타입
     * @param portonePaymentId PortOne 결제 ID
     * @param rawPayload 서명 검증에 사용한 원본 요청 본문
     * @return RECEIVED 상태의 웹훅 이벤트
     */
    public static PortoneWebhookEvent received(
            String webhookId,
            String type,
            String portonePaymentId,
            String rawPayload
    ) {
        return received(webhookId, type, portonePaymentId, null, rawPayload);
    }

    public static PortoneWebhookEvent received(
            String webhookId,
            String type,
            String portonePaymentId,
            String portoneCancellationId,
            String rawPayload
    ) {
        PortoneWebhookEvent event = new PortoneWebhookEvent();
        event.webhookId = webhookId;
        event.status = WebhookEventStatus.RECEIVED;
        event.type = type;
        event.portonePaymentId = portonePaymentId;
        event.portoneCancellationId = portoneCancellationId;
        event.rawPayload = rawPayload;
        return event;
    }

    /**
     * 지원하지 않는 웹훅 이벤트를 무시 상태로 생성한다.
     *
     * @param webhookId PortOne 웹훅 헤더의 메시지 ID
     * @param type 웹훅 이벤트 타입
     * @param portonePaymentId PortOne 결제 ID
     * @param rawPayload 서명 검증에 사용한 원본 요청 본문
     * @param failureReason 이벤트를 무시한 이유
     * @return IGNORE 상태의 웹훅 이벤트
     */
    public static PortoneWebhookEvent ignored(
            String webhookId,
            String type,
            String portonePaymentId,
            String rawPayload,
            String failureReason
    ) {
        PortoneWebhookEvent event = received(webhookId, type, portonePaymentId, rawPayload);
        event.status = WebhookEventStatus.IGNORE;
        event.failureReason = failureReason;
        event.processedAt = LocalDateTime.now();
        return event;
    }

    /**
     * 웹훅 이벤트를 결제 확정 완료 상태로 변경한다.
     *
     * <p>성공적으로 완료된 내부 결제를 연결하고, 이전 실패 사유가 남아 있지 않도록 초기화한다.
     *
     * @param payment 웹훅으로 확정 처리된 내부 결제
     */
    public void markProcessed(Payment payment) {
        markProcessed(payment, null);
    }

    /**
     * 웹훅 이벤트를 환불 처리 완료 상태로 변경한다.
     *
     * @param payment 웹훅으로 환불 처리된 내부 결제
     * @param refund 웹훅으로 완료 처리된 내부 환불
     */
    public void markProcessed(Payment payment, Refund refund) {
        this.payment = payment;
        this.refund = refund;
        this.status = WebhookEventStatus.PROCESSED;
        this.failureReason = null;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 웹훅 이벤트를 처리 실패 상태로 변경한다.
     *
     * @param failureReason 실패 원인 코드 또는 예외 클래스명
     */
    public void markFailed(String failureReason) {
        this.status = WebhookEventStatus.FAILED;
        this.failureReason = failureReason;
        this.processedAt = LocalDateTime.now();
    }
}
