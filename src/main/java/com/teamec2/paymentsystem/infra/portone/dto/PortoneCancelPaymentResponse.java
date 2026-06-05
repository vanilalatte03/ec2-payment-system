package com.teamec2.paymentsystem.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

/**
 * PortOne V2 결제 취소 API 응답 본문.
 *
 * <p>PortOne 응답에는 현재 코드에서 사용하지 않는 필드가 더 있을 수 있으므로
 * {@link JsonIgnoreProperties#ignoreUnknown()} 설정으로 알 수 없는 필드는 무시한다.
 *
 * @param cancellation PortOne 취소 결과 상세 정보
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortoneCancelPaymentResponse(
        Cancellation cancellation
) {
    /**
     * PortOne 결제 취소 결과 상세 정보.
     *
     * @param id PortOne 취소 ID
     * @param status PortOne 취소 상태
     * @param totalAmount 취소된 총 금액
     * @param createdAt PortOne 취소 생성 시각
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cancellation(
            String id,
            String status,
            Long totalAmount,
            OffsetDateTime createdAt
    ) {
    }
}
