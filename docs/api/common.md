# 공통 API 규칙

## Base URL

- 로컬 실행 기준: `http://localhost:8080`
- 모든 서비스 API prefix: `/api`
- 요청/응답 Content-Type: `application/json; charset=UTF-8`
- 금액 단위: 원화 정수. 소수점 금액은 사용하지 않습니다.
- 일시 형식: ISO 8601 문자열. 예: `2026-05-29T18:30:00+09:00`

## 인증

JWT Bearer 토큰을 사용합니다.

```http
Authorization: Bearer {accessToken}
```

인증이 필요 없는 API는 다음과 같습니다.

- `POST /api/auth/signup`
- `POST /api/auth/login`
- `GET /api/products`
- `GET /api/products/{productId}`

`POST /api/webhooks/portone`은 JWT가 아니라 PortOne 웹훅 서명을 검증합니다.

## 공통 응답

성공과 실패 모두 같은 wrapper를 사용합니다. 첨부된 기존 API 명세 예시를 따라 응답 body의 `status`는 `200`으로 고정하고, 실제 HTTP 상태 코드는 엔드포인트별 에러 표의 값을 따릅니다.

성공 응답:

```json
{
  "status": 200,
  "message": "요청이 성공했습니다.",
  "data": {}
}
```

실패 응답:

```json
{
  "status": 200,
  "code": "VALIDATION_FAILED",
  "message": "요청 본문, 쿼리 파라미터, 경로 변수 검증에 실패했습니다.",
  "data": [
    "email은 필수입니다."
  ]
}
```

`data`가 `null`이면 응답에서 생략할 수 있습니다.

## 페이지네이션

목록 API는 다음 query parameter를 사용합니다.

| 이름 | 타입 | 기본값  | 설명 |
| --- | --- |------| --- |
| `page` | int | `0`  | 0부터 시작하는 페이지 번호 |
| `size` | int | `10` | 페이지 크기. 최대 `100` |

페이지 응답 형식:

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 120,
  "totalPages": 6,
  "hasNext": true
}
```

## 멱등성

- `payment.portone_payment_id`는 UNIQUE입니다.
- 결제 확정 API와 웹훅은 같은 `portonePaymentId`가 여러 번 들어와도 최종 상태가 동일해야 합니다.
- 이미 완료된 결제 확정 요청은 상태를 변경하지 않고 성공 응답을 반환합니다.

## Enum

### ProductStatus

| 값 | 설명 |
| --- | --- |
| `ON_SALE` | 판매중 |
| `SOLD_OUT` | 품절 |
| `DISCONTINUED` | 판매 중지 |

### OrderStatus

| 값 | 설명 |
| --- | --- |
| `PAYMENT_PENDING` | 결제대기 |
| `COMPLETED` | 주문완료 |
| `PARTIAL_CANCELED` | 일부 주문상품 취소 |
| `CANCELED` | 주문취소 |

### OrderItemStatus

| 값 | 설명 |
| --- | --- |
| `ORDERED` | 주문됨 |
| `CANCELED` | 주문상품 취소 |

### PaymentStatus

| 값 | 설명 |
| --- | --- |
| `PENDING` | 결제대기 |
| `COMPLETED` | 결제완료 |
| `FAILED` | 결제실패 |
| `PARTIAL_REFUNDED` | 부분환불 |
| `FULL_REFUNDED` | 전액환불 |

### PaymentType

| 값 | 설명 |
| --- | --- |
| `CARD` | 카드 결제 |
| `POINT_CARD` | 포인트 + 카드 복합 결제 |
| `POINT_ONLY` | 포인트 전액 결제 |

### PointTransactionType

| 값 | 설명 |
| --- | --- |
| `SIGNUP_BONUS` | 회원가입 시 가입 축하 포인트 지급 |
| `USE_RESERVE` | 주문 생성 시 사용 포인트 예약 |
| `USE` | 결제 확정 시 예약 포인트 최종 사용 처리 |
| `USE_CANCEL` | 결제 실패 또는 주문 취소 시 예약 포인트 취소/복구 |
| `EARN` | 결제 완료 후 PG 실결제 금액 기준 포인트 적립 |
| `USE_RESTORE` | 환불에 따른 사용 포인트 복구 |
| `EARN_CANCEL` | 환불에 따른 적립 포인트 회수 |
| `EARN_RECOVERY_RESERVE` | 환불 요청 시 적립 포인트 회수 예약 |
| `EARN_RECOVERY_RELEASE` | PG 환불 실패 시 적립 포인트 회수 예약 해제 |

### RefundStatus

| 값 | 설명 |
| --- | --- |
| `PROCESSING` | DB 환불 처리 완료, PG 취소 호출 전/진행 중 |
| `COMPLETED` | 환불 완료 |
| `FAILED` | PG 취소 실패 또는 환불 처리 실패 |
| `PG_RESULT_UNKNOWN` | PortOne 취소 요청 결과를 확정하지 못한 상태. 타임아웃/네트워크 오류 후 재조회 대상 |

## 공통 에러 코드

| 코드 | HTTP | 설명 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 요청 본문, 쿼리 파라미터, 경로 변수 검증 실패 |
| `INVALID_ENUM_VALUE` | 400 | 허용하지 않는 Enum 값 |
| `MISSING_REQUIRED_FIELD` | 400 | 필수 값 누락 |
| `INVALID_PAGINATION` | 400 | 페이지 번호 또는 크기 오류 |
| `UNAUTHORIZED` | 401 | 인증 토큰 누락 또는 인증 실패 |
| `INVALID_TOKEN` | 401 | 잘못된 JWT |
| `EXPIRED_TOKEN` | 401 | 만료된 JWT |
| `FORBIDDEN` | 403 | 권한 또는 소유권 없음 |
| `RESOURCE_NOT_FOUND` | 404 | 리소스 없음 |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 HTTP method |
| `CONFLICT` | 409 | 현재 상태와 충돌하는 요청 |
| `DUPLICATE_REQUEST` | 409 | 중복 요청 |
| `EXTERNAL_API_FAILED` | 502 | 외부 API 호출 실패 |
| `INTERNAL_SERVER_ERROR` | 500 | 서버 내부 오류 |

## 도메인 에러 코드 카탈로그

| 코드 | HTTP | 설명 |
| --- | --- | --- |
| `EMAIL_ALREADY_EXISTS` | 409 | 이미 가입된 이메일 |
| `INVALID_LOGIN_CREDENTIALS` | 401 | 이메일 또는 비밀번호 불일치 |
| `USER_NOT_FOUND` | 404 | 회원 없음 |
| `PRODUCT_NOT_FOUND` | 404 | 상품 없음 |
| `PRODUCT_NOT_ON_SALE` | 400 | 판매중이 아닌 상품 |
| `PRODUCT_OUT_OF_STOCK` | 409 | 상품 재고 부족 |
| `CART_NOT_FOUND` | 404 | 장바구니 없음 |
| `CART_ITEM_NOT_FOUND` | 404 | 장바구니 상품 없음 |
| `CART_ITEM_ACCESS_DENIED` | 403 | 타인의 장바구니 상품 접근 |
| `CART_EMPTY` | 400 | 주문 가능한 장바구니 상품 없음 |
| `CART_STOCK_EXCEEDED` | 409 | 장바구니 수량이 재고 초과 |
| `ORDER_NOT_FOUND` | 404 | 주문 없음 |
| `ORDER_ACCESS_DENIED` | 403 | 타인의 주문 접근 |
| `ORDER_ITEM_NOT_FOUND` | 404 | 주문 상품 없음 |
| `ORDER_STOCK_SHORTAGE` | 409 | 주문 생성 중 재고 부족 |
| `INVALID_ORDER_STATUS` | 400 | 요청할 수 없는 주문 상태 |
| `ORDER_CANCEL_NOT_ALLOWED` | 409 | 결제대기 상태가 아니라 직접 취소 불가 |
| `INVALID_ORDER_PRICE` | 400 | 주문 가격 오류 |
| `INVALID_ORDER_QUANTITY` | 400 | 주문 수량 오류 |
| `INVALID_ORDER_STOCK` | 400 | 주문 재고 오류 |
| `INVALID_USED_POINT` | 400 | 사용 포인트 오류 |
| `PAYMENT_NOT_FOUND` | 404 | 결제 없음 |
| `PAYMENT_ACCESS_DENIED` | 403 | 타인의 결제 접근 |
| `PAYMENT_ALREADY_PROCESSED` | 200 | 이미 완료된 결제 확정 요청 |
| `PAYMENT_IN_PROGRESS` | 409 | 같은 결제 건 처리 중 |
| `PAYMENT_PORTONE_ID_MISMATCH` | 400 | 주문의 결제 식별자와 요청 식별자 불일치 |
| `PAYMENT_STATUS_NOT_PAID` | 400 | PortOne 결제 상태가 성공 상태가 아님 |
| `PAYMENT_AMOUNT_MISMATCH` | 400 | PortOne 승인 금액과 서버 산정 PG 금액 불일치 |
| `PAYMENT_COMPENSATION_FAILED` | 502 | 외부 성공/내부 실패 보상 취소 실패 |
| `INSUFFICIENT_POINT` | 400 | 포인트 잔액 부족 |
| `REFUND_IN_PROGRESS` | 409 | 동일 결제의 환불 처리 진행 중 |
| `POINT_ACCOUNT_NOT_FOUND` | 404 | 포인트 계정 없음 |
| `POINT_LEDGER_SYNC_FAILED` | 500 | 포인트 스냅샷과 원장 동기화 실패 |
| `REFUND_ITEM_REQUIRED` | 400 | 환불할 주문 상품 목록 누락 |
| `REFUND_QUANTITY_EXCEEDED` | 400 | 잔여 환불 가능 수량 초과 |
| `REFUND_NOT_ALLOWED` | 409 | 환불 가능한 결제 상태가 아님 |
| `REFUND_PG_CANCEL_FAILED` | 502 | PG 취소 실패 |
| `INVALID_REFUND_STATUS` | 409 | 현재 환불 상태에서 수행할 수 없는 작업 |
| `PAYMENT_CANCEL_REQUEST_FAILED` | 502 | 외부 결제 취소 요청 실패 |
| `INVALID_REFUND_OUTBOX_STATUS` | 409 | 현재 환불 Outbox 상태에서 수행할 수 없는 작업 |
| `WEBHOOK_SIGNATURE_INVALID` | 400 | 웹훅 서명 검증 실패 |
| `WEBHOOK_PAYLOAD_INVALID` | 400 | 웹훅 본문 파싱 실패 |
| `WEBHOOK_PAYMENT_ID_MISSING` | 400 | 웹훅에서 paymentId 추출 실패 |
