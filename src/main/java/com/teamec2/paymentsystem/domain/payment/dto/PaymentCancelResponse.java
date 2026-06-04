package com.teamec2.paymentsystem.domain.payment.dto;

/**
 * 외부 결제 취소 결과를 도메인 계층에 전달하기 위한 응답 객체.
 *
 * <p>PortOne 응답 DTO를 서비스 계층에 그대로 노출하지 않기 위해 별도 DTO로 변환한다.
 * 현재는 보상 취소가 성공했는지 확인하는 데 필요한 최소 값만 담는다.
 *
 * @param cancellationId PortOne이 발급한 취소 ID
 * @param status PortOne 취소 상태
 */
public record PaymentCancelResponse(
        String cancellationId,
        String status
) {

    private static final String STATUS_SUCCEEDED = "SUCCEEDED";

    /**
     * PortOne 취소가 실제 완료 상태인지 확인한다.
     *
     * <p>취소 응답이 왔더라도 {@code REQUESTED}처럼 아직 처리 중인 상태일 수 있다.
     * 우리 내부 주문 취소, 결제 실패, 재고 복구는 PortOne 취소가 완료된 뒤에만 반영해야 하므로
     * {@code SUCCEEDED}만 성공으로 인정한다.
     *
     * @return PortOne 취소 완료 여부
     */
    public boolean isSucceeded() {
        return STATUS_SUCCEEDED.equals(status);
    }
}
