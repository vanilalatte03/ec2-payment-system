package com.teamec2.paymentsystem.domain.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record CreateRequest(
        List<Long> cartItemIds,

        @NotNull
        @PositiveOrZero
        Long usedPointAmount
) {
}
