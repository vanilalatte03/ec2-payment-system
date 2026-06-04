package com.teamec2.paymentsystem.infra.portone.webhook.entity;

import com.teamec2.paymentsystem.domain.payment.entity.Payment;
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

    // TODO: 환불 도메인 구현 후 연결한다.
    // @ManyToOne(fetch = FetchType.LAZY, optional = false)
    // @JoinColumn(name = "refund_id")
    // private Refund refund;

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
        PortoneWebhookEvent event = new PortoneWebhookEvent();
        event.webhookId = webhookId;
        event.status = WebhookEventStatus.RECEIVED;
        event.type = type;
        event.portonePaymentId = portonePaymentId;
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

}
