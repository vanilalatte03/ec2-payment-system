package com.teamec2.paymentsystem.domain.point.dto;

import com.teamec2.paymentsystem.domain.point.entity.PointTransaction;
import com.teamec2.paymentsystem.domain.point.enums.PointTransactionType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PointTransactionResponse {

    private final Long pointTransactionId;
    private final Long paymentId;
    private final PointTransactionType type;
    private final Long amount;
    private final LocalDateTime createdAt;

    public static PointTransactionResponse from(PointTransaction transaction) {
        return new PointTransactionResponse(
                transaction.getId(),
                transaction.getPayment() == null ? null : transaction.getPayment().getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCreatedAt()
        );
    }
}
