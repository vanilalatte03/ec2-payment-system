package com.teamec2.paymentsystem.domain.refund.service;

import com.teamec2.paymentsystem.domain.refund.dto.FullRefundRequest;
import com.teamec2.paymentsystem.domain.refund.dto.PartialRefundRequest;
import com.teamec2.paymentsystem.domain.refund.dto.RefundItemRequest;
import com.teamec2.paymentsystem.domain.refund.entity.Refund;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * 환불 요청의 멱등성(Idempotency)을 관리합니다.
 * 이 클래스는 다음 책임을 가집니다.
 * 1. 멱등키 필수값 검증
 * 2. 부분 환불 요청 해시 생성
 * 3. 전체 환불 요청 해시 생성
 * 4. 같은 멱등키로 들어온 요청이 기존 요청과 같은 요청인지 검증
 * 실제 Refund 조회, 생성, 저장은 담당하지 않습니다.
 */
@Component
public class RefundIdempotencyService {
    /**
     * 멱등키가 비어 있는지 검증합니다.
     * 멱등키는 같은 환불 요청이 중복 생성되지 않도록 막기 위한 키입니다.
     */
    public void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }
    }

    /**
     * 같은 Idempotency-Key로 이미 생성된 환불 요청이 있을 때,
     * 기존 요청과 현재 요청의 내용이 같은지 검증합니다.
     * 같은 키인데 요청 내용이 다르면 재시도가 아니라 충돌이 납니다!
     */
    public void validateSameIdempotentRequest(Refund existingRefund, String requestHash) {
        if (!existingRefund.getRequestHash().equals(requestHash)) {
            /*
             * 같은 Idempotency-Key인데 요청 내용이 다르면 재시도가 아니라 충돌입니다.
             * 기존 환불을 그대로 반환하면 사용자는 다른 요청이 성공한 것처럼 오해할 수 있습니다.
             */
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }

    /**
     * 부분 환불 요청 내용을 기준으로 요청 해시를 생성합니다.
     * item 순서가 달라도 같은 요청이면 같은 해시가 나오도록
     * orderItemId 기준으로 정렬한 뒤 해시를 생성합니다.
     */
    public String createPartialRefundRequestHash(PartialRefundRequest request) {
        String itemKey = request.items().stream()
                .sorted(Comparator.comparing(RefundItemRequest::orderItemId))
                .map(item -> item.orderItemId() + ":" + item.quantity())
                .collect(Collectors.joining("|"));

        return sha256(request.reason() + "|" + itemKey);
    }

    /**
     * 전체 환불 요청 내용을 기준으로 요청 해시를 생성합니다.
     *
     * 현재 전체 환불은 사유(reason)만 요청 본문에 포함되므로
     * reason을 기준으로 해시를 생성합니다.
     */
    public String createFullRefundRequestHash(FullRefundRequest request) {
        return sha256(request.reason());
    }

    /**
     * 문자열 값을 SHA-256 해시 문자열로 변환합니다.
     */
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(value.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();

            for (byte b : encodedHash) {
                hexString.append(String.format("%02x", b));
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
