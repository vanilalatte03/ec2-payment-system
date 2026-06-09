package com.teamec2.paymentsystem.domain.auth.dto;

import java.time.LocalDateTime;

public record SignupResponse(
        Long userId,
        String email,
        String name,
        String phone,
        LocalDateTime createdAt
) {
}
