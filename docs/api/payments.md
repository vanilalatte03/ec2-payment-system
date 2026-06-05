# 결제 API

결제 확정은 서버가 최종 책임자입니다. 클라이언트 콜백과 PortOne 웹훅은 순서가 바뀌거나 중복될 수 있으므로 같은 도메인 서비스를 호출해 멱등하게 처리해야 합니다.

## 결제 확정

PortOne SDK 결제 완료 후 클라이언트가 서버에 결제 확정을 요청합니다. 포인트 전액 결제(`pgAmount = 0`)도 이 API를 호출하되 PortOne 조회는 생략합니다.

- Method: `POST`
- Path: `/api/payments/confirm`
- 인증: 필요
- HTTP Status: `200 OK`

### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `orderId` | Long | Y | 주문 ID |
| `portonePaymentId` | string | Y | 주문 생성 시 서버가 반환한 PortOne 결제 ID |

```json
{
  "orderId": 200,
  "portonePaymentId": "pay_20260529_000001"
}
```

### Response Data

```json
{
  "orderId": 200,
  "orderNumber": "ORD-20260529-550E8400E29B41D4A716446655440000",
  "orderStatus": "COMPLETED",
  "paymentId": 300,
  "portonePaymentId": "pay_20260529_000001",
  "paymentStatus": "COMPLETED",
  "paymentType": "POINT_CARD",
  "totalAmount": 78000,
  "usedPointAmount": 5000,
  "pgAmount": 73000,
  "rewardPointAmount": 730,
  "cartCleared": true,
  "approvedAt": "2026-05-29T18:35:00+09:00"
}
```

`cartCleared`는 결제 확정 과정에서 주문에 포함된 장바구니 상품을 1건 이상 삭제했는지 나타냅니다.
선택 주문이었다면 주문하지 않은 장바구니 상품은 삭제하지 않습니다.

이미 완료된 결제 확정 요청이면 같은 형태의 성공 응답을 반환하고 추가 포인트/재고/장바구니 처리는 수행하지 않습니다.

### 처리 규칙

- 소유권, 주문 상태, 결제 상태, `portonePaymentId` 일치 여부를 먼저 검증합니다.
- `pgAmount > 0`이면 PortOne 결제 단건 조회 API로 실제 결제 정보를 조회합니다.
- PortOne 상태가 성공이고 승인 금액이 서버 산정 PG 금액과 정확히 일치해야 합니다.
- 검증 통과 시 단일 트랜잭션으로 주문 완료, 결제 완료, 포인트 사용 원장, 포인트 적립 원장, 포인트 잔액 갱신, 주문 상품에 해당하는 장바구니 상품 삭제를 처리합니다.
- 장바구니에 5개의 상품이 있고 그중 2개만 주문했다면, 결제 완료 후 주문한 2개만 삭제되고 나머지 3개는 유지됩니다.
- 검증 실패 또는 금액 불일치가 외부 결제 성공 후 발견되면 PortOne 결제 취소 API로 보상 취소한 뒤 주문 취소, 결제 실패, 재고 복구를 처리합니다.
- 적립 포인트는 기본 정책에서 PG 실결제 금액의 1%입니다.

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `VALIDATION_FAILED` | 400 | `orderId` 또는 `portonePaymentId` 누락 |
| `ORDER_NOT_FOUND` | 404 | 주문 없음 |
| `ORDER_ACCESS_DENIED` | 403 | 타인의 주문 |
| `PAYMENT_NOT_FOUND` | 404 | 결제 없음 |
| `PAYMENT_PORTONE_ID_MISMATCH` | 400 | 주문의 결제 식별자와 요청 식별자 불일치 |
| `INVALID_ORDER_STATUS` | 400 | 주문이 결제대기 상태가 아님 |
| `PAYMENT_ALREADY_PROCESSED` | 200 | 이미 완료된 결제. 성공 응답으로 처리 |
| `PAYMENT_IN_PROGRESS` | 409 | 같은 결제 건 처리 중 |
| `PAYMENT_STATUS_NOT_PAID` | 400 | PortOne 결제 상태가 성공 상태가 아님 |
| `PAYMENT_AMOUNT_MISMATCH` | 400 | PortOne 승인 금액과 서버 산정 PG 금액 불일치 |
| `EXTERNAL_API_FAILED` | 502 | PortOne 결제 조회 실패 |
| `PAYMENT_COMPENSATION_FAILED` | 502 | 보상 취소 실패 |
