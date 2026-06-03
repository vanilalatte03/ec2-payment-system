package com.teamec2.paymentsystem.domain.point.service;


import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 포인트 정책: PG 실결제 금액의 1% 를 적립합니다.
 */

@Component
public class PointPolicy {

    public Long calculateRewardPoint(Long pgAmount) {

        if (pgAmount == null || pgAmount < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return pgAmount / 100;
    }
}
