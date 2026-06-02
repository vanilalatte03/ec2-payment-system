package com.teamec2.paymentsystem.infra.portone.client;

import com.teamec2.paymentsystem.domain.payment.port.PaymentGateway;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGatewayResponse;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import com.teamec2.paymentsystem.infra.portone.config.PortoneProperties;
import com.teamec2.paymentsystem.infra.portone.dto.PortonePaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class PortoneClient implements PaymentGateway {

    private final RestClient portoneRestClient;
    private final PortoneProperties portoneProperties;

    @Override
    public PaymentGatewayResponse getPayment(String paymentId) {
        log.info("PortOne 결제 조회: {}", paymentId);

        try {
            PortonePaymentResponse response = portoneRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/payments/{paymentId}")
                            .queryParam("storeId", portoneProperties.storeId())
                            .build(paymentId))
                    .retrieve()
                    .body(PortonePaymentResponse.class);

            validateResponse(response);

            return new PaymentGatewayResponse(
                    response.id(),
                    response.status(),
                    response.amount().total(),
                    response.paidAt() == null ? null : response.paidAt().atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime()
            );
        } catch (RestClientException e) {
            log.warn("PortOne 결제 조회 실패: paymentId={}", paymentId, e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_FAILED);
        }
    }

    private void validateResponse(PortonePaymentResponse response) {
        if (response == null
                || response.id() == null
                || response.status() == null
                || response.amount() == null
                || response.amount().total() == null) {
            log.warn("PortOne 결제 조회 응답 형식 오류");
            throw new BusinessException(ErrorCode.EXTERNAL_API_FAILED);
        }
    }
}