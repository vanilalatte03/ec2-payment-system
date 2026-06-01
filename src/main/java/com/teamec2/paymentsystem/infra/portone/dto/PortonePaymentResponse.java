package com.teamec2.paymentsystem.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PortonePaymentResponse(
        String id,
        String status,
        PaymentAmount amount,
        OffsetDateTime paidAt
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentAmount(Long total) {

    }
}
