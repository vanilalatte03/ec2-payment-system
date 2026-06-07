package com.teamec2.paymentsystem.domain.payment.dto;

import com.teamec2.paymentsystem.domain.payment.port.PaymentCancelStatus;

/**
 * 외부 결제 취소 결과를 도메인 계층에 전달하기 위한 응답 객체.
 *
 * <p>PortOne 응답 DTO를 서비스 계층에 그대로 노출하지 않기 위해 별도 DTO로 변환한다.
 * 현재는 보상 취소가 성공했는지 확인하는 데 필요한 최소 값만 담는다.
 *
 * <p>PortOne의 원본 상태 문자열은 {@code rawStatus}에 보관하고,
 * 우리 서비스 기준으로 해석한 취소 상태는 {@code cancelStatus}에 보관합니다.
 *
 * @param cancellationId PortOne이 발급한 취소 ID
 * @param rawStatus PortOne이 내려준 원본 취소 상태
 * @param cancelStatus 우리 서비스 기준으로 해석한 취소 상태
 */
public record PaymentCancelResponse(
        String cancellationId,
        String rawStatus,
        PaymentCancelStatus cancelStatus
) {

    /**
     * PortOne 취소가 실제 완료 상태인지 확인한다.
     *
     * <p>취소 응답이 왔더라도 {@code REQUESTED}처럼 아직 처리 중인 상태일 수 있다.
     * 우리 내부 주문 취소, 결제 실패, 재고 복구는 PortOne 취소가 완료된 뒤에만 반영해야 하므로
     * SUCCEEDED만 성공으로 인정한다.
     */
    public boolean isSucceeded() {
        return cancelStatus == PaymentCancelStatus.SUCCEEDED;
    }

    /**
     * 외부 결제 취소가 명확히 실패했는지 확인합니다.
     */
    public boolean isFailed() {
        return cancelStatus == PaymentCancelStatus.FAILED;
    }

    /**
     * 외부 결제 취소 결과가 아직 성공/실패로 확정되지 않았는지 확인합니다.
     *
     * <p>REQUESTED 같은 상태는 실패가 아니므로,
     * 환불 도메인에서는 PG_RESULT_UNKNOWN 또는 재시도/재조회 대상으로 처리해야 합니다.
     */
    public boolean isResultUnknown() {
        return cancelStatus == PaymentCancelStatus.RESULT_UNKNOWN;
    }
}
