package com.teamec2.paymentsystem.infra.portone.config;

import com.teamec2.paymentsystem.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/portone-config")
@RequiredArgsConstructor
public class PortoneBrowserConfigController {

    private final PortoneProperties portoneProperties;

    @GetMapping
    public ResponseEntity<ApiResponse<PortoneBrowserConfigResponse>> getPortoneBrowserConfig() {
        return ResponseEntity.ok(ApiResponse.success(PortoneBrowserConfigResponse.from(portoneProperties)));
    }
}
