package com.teamec2.paymentsystem.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // global
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 본문, 쿼리 파라미터, 경로 변수 검증에 실패했습니다."),
    INVALID_ENUM_VALUE(HttpStatus.BAD_REQUEST, "허용하지 않는 Enum 값입니다."),
    MISSING_REQUIRED_FIELD(HttpStatus.BAD_REQUEST, "필수 값이 누락되었습니다."),
    INVALID_PAGINATION(HttpStatus.BAD_REQUEST, "페이지 번호 또는 크기가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증 토큰이 누락되었거나 인증에 실패했습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "잘못된 JWT입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 JWT입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한 또는 소유권이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP method입니다."),
    CONFLICT(HttpStatus.CONFLICT, "현재 상태와 충돌하는 요청입니다."),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "중복 요청입니다."),
    EXTERNAL_API_FAILED(HttpStatus.BAD_GATEWAY, "외부 API 호출에 실패했습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류입니다."),

    // Auth
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    INVALID_LOGIN_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),

    // Product
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    PRODUCT_NOT_ON_SALE(HttpStatus.BAD_REQUEST, "판매중이 아닌 상품입니다."),
    PRODUCT_OUT_OF_STOCK(HttpStatus.CONFLICT, "상품 재고가 부족합니다."),
    INVALID_PRICE(HttpStatus.BAD_REQUEST, "가격은 0 이상이어야 합니다."),
    INVALID_STOCK(HttpStatus.BAD_REQUEST,"재고는 0 이상이어야 합니다."),
    INVALID_RESTORE_STOCK_QUANTITY(HttpStatus.BAD_REQUEST, "취소 수량은 1개 이상이어야 합니다."),

    // Cart
    CART_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니를 찾을 수 없습니다."),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니 상품을 찾을 수 없습니다."),
    CART_ITEM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "타인의 장바구니 상품에 접근할 수 없습니다."),
    CART_EMPTY(HttpStatus.BAD_REQUEST, "주문 가능한 장바구니 상품이 없습니다."),
    CART_STOCK_EXCEEDED(HttpStatus.CONFLICT, "장바구니 수량이 재고를 초과했습니다."),

    // Order
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "타인의 주문에 접근할 수 없습니다."),
    ORDER_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "주문 상품을 찾을 수 없습니다."),
    ORDER_STOCK_SHORTAGE(HttpStatus.CONFLICT, "주문 생성 중 재고가 부족합니다."),
    INVALID_ORDER_STATUS(HttpStatus.BAD_REQUEST, "요청할 수 없는 주문 상태입니다."),
    ORDER_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "결제대기 상태가 아니라 직접 취소할 수 없습니다."),
    INVALID_ORDER_PRICE(HttpStatus.BAD_REQUEST, "가격은 0 이상이어야 합니다."),
    INVALID_ORDER_QUANTITY(HttpStatus.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다."),
    INVALID_ORDER_STOCK(HttpStatus.BAD_REQUEST,"재고는 0 이상이어야 합니다."),
    INVALID_USED_POINT(HttpStatus.BAD_REQUEST, "사용 포인트는 0 이상이고 주문 금액 이하여야 합니다."),

    // Payment
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다."),
    PAYMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "타인의 결제에 접근할 수 없습니다."),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.OK, "이미 완료된 결제입니다."),
    PAYMENT_IN_PROGRESS(HttpStatus.CONFLICT, "같은 결제 건이 처리 중입니다."),
    PAYMENT_PORTONE_ID_MISMATCH(HttpStatus.BAD_REQUEST, "주문의 결제 식별자와 요청 식별자가 일치하지 않습니다."),
    PAYMENT_STATUS_NOT_PAID(HttpStatus.BAD_REQUEST, "PortOne 결제 상태가 성공 상태가 아닙니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PortOne 승인 금액과 서버 산정 PG 금액이 일치하지 않습니다."),
    PAYMENT_COMPENSATION_FAILED(HttpStatus.BAD_GATEWAY, "외부 성공/내부 실패 보상 취소에 실패했습니다."),

    // Point
    POINT_INCREASE_AMOUNT_INVALID(HttpStatus.BAD_REQUEST, "증가할 포인트는 0보다 커야 합니다."),
    POINT_DECREASE_AMOUNT_INVALID(HttpStatus.BAD_REQUEST, "차감할 포인트는 0보다 커야 합니다."),
    // PointTransaction
    INSUFFICIENT_POINT(HttpStatus.BAD_REQUEST, "포인트 잔액이 부족합니다."),
    POINT_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "포인트 계정을 찾을 수 없습니다."),
    POINT_LEDGER_SYNC_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "포인트 스냅샷과 원장 동기화에 실패했습니다."),
    POINT_ERROR_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR, "포인트 관련 예외가 발생했습니다."),
    INVALID_POINT_TRANSACTION_AMOUNT(HttpStatus.BAD_REQUEST, "포인트 거래 금액은 0보다 커야 합니다."),
    INVALID_POINT_TRANSACTION_TYPE(HttpStatus.BAD_REQUEST, "포인트 거래 유형이 올바르지 않습니다."),

    // Refund
    REFUND_ITEM_REQUIRED(HttpStatus.BAD_REQUEST, "환불할 주문 상품 목록이 필요합니다."),
    INVALID_REFUND_QUANTITY(HttpStatus.BAD_REQUEST, "환불 수량은 1개 이상이어야 합니다."),
    REFUND_QUANTITY_EXCEEDED(HttpStatus.BAD_REQUEST, "잔여 환불 가능 수량을 초과했습니다."),
    REFUND_NOT_ALLOWED(HttpStatus.CONFLICT, "환불 가능한 결제 상태가 아닙니다."),
    REFUND_PG_CANCEL_FAILED(HttpStatus.BAD_GATEWAY, "PG 취소에 실패했습니다."),
    REFUND_IN_PROGRESS(HttpStatus.CONFLICT, "같은 결제 건의 환불이 처리 중입니다."),
    INVALID_REFUND_STATUS(HttpStatus.CONFLICT, "현재 환불 상태에서는 수행할 수 없는 작업입니다."),
    PAYMENT_CANCEL_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "외부 결제 취소 요청에 실패했습니다."),

    // RefundOutbox
    INVALID_REFUND_OUTBOX_STATUS(HttpStatus.CONFLICT, "현재 아웃박스 상태에서는 수행할 수 없는 작업입니다."),

    // Webhook
    WEBHOOK_SIGNATURE_INVALID(HttpStatus.BAD_REQUEST, "웹훅 서명 검증에 실패했습니다."),
    WEBHOOK_PAYLOAD_INVALID(HttpStatus.BAD_REQUEST, "웹훅 본문 파싱에 실패했습니다."),
    WEBHOOK_PAYMENT_ID_MISSING(HttpStatus.BAD_REQUEST, "웹훅에서 paymentId를 추출하지 못했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
