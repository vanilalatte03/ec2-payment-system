package com.teamec2.paymentsystem.domain.auth.dto;

public record LoginResponse(
        Long userId,
        String email,
        String name
) {
}
