package com.teamec2.paymentsystem.infra.portone.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PortonePaymentResponseTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void PortOne응답_JSON을_DTO로_변환한다() throws Exception {
        // given
        String json = """
                {
                  "id": "pay_123",
                  "status": "PAID",
                  "amount": {
                    "total": 73000
                  },
                  "paidAt": "2026-06-01T18:35:00+09:00"
                }
                """;

        // when
        PortonePaymentResponse response = objectMapper.readValue(json, PortonePaymentResponse.class);

        // then
        assertThat(response.id()).isEqualTo("pay_123");
        assertThat(response.status()).isEqualTo("PAID");
        assertThat(response.amount().total()).isEqualTo(73000L);
        assertThat(response.paidAt()).isEqualTo(OffsetDateTime.parse("2026-06-01T18:35:00+09:00"));
    }

    @Test
    void 알수없는필드가_있어도_역직렬화에_성공한다() throws Exception {
        // given
        String json = """
                {
                  "id": "pay_123",
                  "status": "PAID",
                  "amount": {
                    "total": 73000,
                    "currency": "KRW"
                  },
                  "paidAt": "2026-06-01T18:35:00+09:00",
                  "extraField": "ignored"
                }
                """;

        // when
        PortonePaymentResponse response = objectMapper.readValue(json, PortonePaymentResponse.class);

        // then
        assertThat(response.id()).isEqualTo("pay_123");
        assertThat(response.amount().total()).isEqualTo(73000L);
    }

    @Test
    void paidAt은_OffsetDateTime으로_역직렬화된다() throws Exception {
        // given
        String json = """
                {
                  "id": "pay_123",
                  "status": "PAID",
                  "amount": {
                    "total": 73000
                  },
                  "paidAt": "2026-06-01T18:35:00+09:00"
                }
                """;

        // when
        PortonePaymentResponse response = objectMapper.readValue(json, PortonePaymentResponse.class);

        // then
        assertThat(response.paidAt()).isEqualTo(OffsetDateTime.parse("2026-06-01T18:35:00+09:00"));
    }

    @Test
    void amount_total을_Long으로_역직렬화한다() throws Exception {
        // given
        String json = """
                {
                  "id": "pay_123",
                  "status": "PAID",
                  "amount": {
                    "total": 2147483648
                  },
                  "paidAt": "2026-06-01T18:35:00+09:00"
                }
                """;

        // when
        PortonePaymentResponse response = objectMapper.readValue(json, PortonePaymentResponse.class);

        // then
        assertThat(response.amount().total()).isEqualTo(2147483648L);
    }
}
