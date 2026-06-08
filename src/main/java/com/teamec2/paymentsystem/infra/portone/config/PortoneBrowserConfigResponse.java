package com.teamec2.paymentsystem.infra.portone.config;

public record PortoneBrowserConfigResponse(
        String storeId,
        String channelKey
) {

    public static PortoneBrowserConfigResponse from(PortoneProperties properties) {
        return new PortoneBrowserConfigResponse(
                properties.storeId(),
                properties.channelKey()
        );
    }
}
