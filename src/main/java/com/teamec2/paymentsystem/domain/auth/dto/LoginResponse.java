package com.teamec2.paymentsystem.domain.auth.dto;

public record LoginResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        UserSummary user
) {

    public record UserSummary(
            Long userId,
            String email,
            String name
    ) {
    }
}
