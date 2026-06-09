package com.teamec2.paymentsystem.domain.point.dto;

import com.teamec2.paymentsystem.domain.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PointBalanceResponse {

    private final Long userId;
    private final Long balance;

    public static PointBalanceResponse from(User user) {
        return new PointBalanceResponse(
                user.getId(),
                user.getPointBalance()
        );
    }
}
