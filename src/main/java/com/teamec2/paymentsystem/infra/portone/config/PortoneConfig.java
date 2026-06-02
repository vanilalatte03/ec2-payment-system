package com.teamec2.paymentsystem.infra.portone.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PortoneProperties.class)
public class PortoneConfig {

    @Bean
    public RestClient portoneRestClient(PortoneProperties portoneProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        return RestClient.builder()
                .baseUrl(portoneProperties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "PortOne " + portoneProperties.apiSecret())
                .requestFactory(requestFactory)
                .build();
    }
}
