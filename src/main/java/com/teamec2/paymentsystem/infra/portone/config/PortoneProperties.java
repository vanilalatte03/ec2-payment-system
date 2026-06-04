package com.teamec2.paymentsystem.infra.portone.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "portone")
public record PortoneProperties(
        @NotBlank String baseUrl,
        @NotBlank String apiSecret,
        @NotBlank String storeId,
        String channelKey,
        @NotBlank String webhookSecret
) {
}
