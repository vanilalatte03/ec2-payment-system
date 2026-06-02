package com.teamec2.paymentsystem.infra.portone.client;

import com.teamec2.paymentsystem.domain.payment.port.PaymentGatewayResponse;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import com.teamec2.paymentsystem.infra.portone.config.PortoneProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PortoneClientTest {

    private static final String BASE_URL = "https://api.portone.io";
    private static final String API_SECRET = "test-api-secret";
    private static final String STORE_ID = "test-store-id";

    private MockRestServiceServer server;
    private PortoneClient portoneClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "PortOne " + API_SECRET);
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();
        PortoneProperties properties = new PortoneProperties(
                BASE_URL,
                API_SECRET,
                STORE_ID,
                "test-channel-key",
                "test-webhook-secret"
        );
        portoneClient = new PortoneClient(restClient, properties);
    }

    @Test
    void 결제조회_정상응답이면_PaymentGatewayResponse로_변환한다() {
        // given
        server.expect(requestTo(BASE_URL + "/payments/pay_123?storeId=" + STORE_ID))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "PortOne " + API_SECRET))
                .andRespond(withSuccess("""
                        {
                          "id": "pay_123",
                          "status": "PAID",
                          "amount": {
                            "total": 73000
                          },
                          "paidAt": "2026-06-01T18:35:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        // when
        PaymentGatewayResponse response = portoneClient.getPayment("pay_123");

        // then
        assertThat(response.paymentId()).isEqualTo("pay_123");
        assertThat(response.status()).isEqualTo("PAID");
        assertThat(response.paidAmount()).isEqualTo(73000L);
        assertThat(response.approvedAt()).isEqualTo(LocalDateTime.of(2026, 6, 2, 3, 35));
        server.verify();
    }

    @Test
    void 결제조회_PortOne서버오류면_EXTERNAL_API_FAILED가발생한다() {
        // given
        server.expect(requestTo(BASE_URL + "/payments/pay_123?storeId=" + STORE_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        // when
        // then
        assertThatThrownBy(() -> portoneClient.getPayment("pay_123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_API_FAILED);
        server.verify();
    }

    @Test
    void 결제조회_응답본문이없으면_EXTERNAL_API_FAILED가발생한다() {
        // given
        server.expect(requestTo(BASE_URL + "/payments/pay_123?storeId=" + STORE_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        // when
        // then
        assertThatThrownBy(() -> portoneClient.getPayment("pay_123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_API_FAILED);
        server.verify();
    }

    @Test
    void 결제조회_응답ID가없으면_EXTERNAL_API_FAILED가발생한다() {
        // given
        server.expect(requestTo(BASE_URL + "/payments/pay_123?storeId=" + STORE_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "status": "PAID",
                          "amount": {
                            "total": 73000
                          },
                          "paidAt": "2026-06-01T18:35:00+09:00"
                        }
                        """, MediaType.APPLICATION_JSON));

        // when
        // then
        assertThatThrownBy(() -> portoneClient.getPayment("pay_123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_API_FAILED);
        server.verify();
    }

    @Test
    void 결제조회_응답상태가없으면_EXTERNAL_API_FAILED가발생한다() {
        // given
        server.expect(requestTo(BASE_URL + "/payments/pay_123?storeId=" + STORE_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "id": "pay_123",
                          "amount": {
                            "total": 73000
                          },
                          "paidAt": "2026-06-01T18:35:00+09:00"
                        }
                        """, MediaType.APPLICATION_JSON));

        // when
        // then
        assertThatThrownBy(() -> portoneClient.getPayment("pay_123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_API_FAILED);
        server.verify();
    }

    @Test
    void 결제조회_응답금액이없으면_EXTERNAL_API_FAILED가발생한다() {
        // given
        server.expect(requestTo(BASE_URL + "/payments/pay_123?storeId=" + STORE_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "id": "pay_123",
                          "status": "PAID",
                          "paidAt": "2026-06-01T18:35:00+09:00"
                        }
                        """, MediaType.APPLICATION_JSON));

        // when
        // then
        assertThatThrownBy(() -> portoneClient.getPayment("pay_123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_API_FAILED);
        server.verify();
    }

    @Test
    void 결제조회_응답금액total이없으면_EXTERNAL_API_FAILED가발생한다() {
        // given
        server.expect(requestTo(BASE_URL + "/payments/pay_123?storeId=" + STORE_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "id": "pay_123",
                          "status": "PAID",
                          "amount": {},
                          "paidAt": "2026-06-01T18:35:00+09:00"
                        }
                        """, MediaType.APPLICATION_JSON));

        // when
        // then
        assertThatThrownBy(() -> portoneClient.getPayment("pay_123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_API_FAILED);
        server.verify();
    }
}
