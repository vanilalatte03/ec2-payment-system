package com.teamec2.paymentsystem.domain.payment.service;

import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentResponse;

/**
 * 결제 확정 Facade와 트랜잭션 서비스 사이에서만 사용하는 내부 전달 객체.
 *
 * <p>Facade인 {@link PaymentService}는 DB 트랜잭션 밖에서 PortOne API를 호출한다.
 * 이때 JPA 엔티티를 그대로 들고 다니면 지연 로딩이나 영속성 컨텍스트 문제를 만날 수 있다.
 * 그래서 외부 API 호출과 보상 취소에 필요한 최소 값만 이 record에 담아 전달한다.
 *
 * @param paymentId 우리 DB의 결제 ID
 * @param portonePaymentId PortOne에 전달할 결제 ID
 * @param pgAmount 우리 서버가 계산한 PG 결제 금액
 * @param pointOnly PG 결제 없이 포인트만으로 결제하는지 여부
 * @param completedResponse 이미 완료된 결제일 때 바로 반환할 멱등 응답. 아직 완료 전이면 {@code null}
 */
record ConfirmPaymentTarget(
        Long paymentId,
        String portonePaymentId,
        Long pgAmount,
        boolean pointOnly,
        ConfirmPaymentResponse completedResponse
) {

    /**
     * 이미 완료된 결제라서 외부 API 조회나 상태 변경 없이 응답만 반환해도 되는지 확인한다.
     *
     * @return 이미 완료된 결제 여부
     */
    boolean alreadyCompleted() {
        return completedResponse != null;
    }
}
