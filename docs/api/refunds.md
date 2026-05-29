# 환불 API

환불 금액은 클라이언트가 입력하지 않습니다. 서버가 주문 상품의 가격 스냅샷과 환불 수량으로 자동 산정합니다.

부분 환불은 원 결제의 포인트/PG 결제 비율에 따라 분리합니다. 기본 산식은 `floor(환불금액 * 사용포인트 / 주문총액)`을 포인트 환불 금액으로 계산하고, PG 환불 금액은 `환불금액 - 포인트환불금액`입니다. 마지막 전액 환불이 되는 요청은 반올림 누적 오차를 없애기 위해 남은 포인트/PG 환불 가능액을 모두 배정합니다.

## 환불 요청

결제 완료된 주문에 대해 부분 또는 전액 환불을 요청합니다.

- Method: `POST`
- Path: `/api/refunds`
- 인증: 필요
- HTTP Status: `201 Created`

### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `orderId` | number | Y | 환불할 주문 ID |
| `reason` | string | Y | 환불 사유 |
| `items` | object[] | Y | 환불할 주문 상품과 수량 |
| `items[].orderItemId` | number | Y | 주문 상품 ID |
| `items[].quantity` | number | Y | 환불 수량. 1 이상 |

```json
{
  "orderId": 200,
  "reason": "단순 변심",
  "items": [
    {
      "orderItemId": 400,
      "quantity": 1
    }
  ]
}
```

### Response Data

```json
{
  "refundId": 700,
  "orderId": 200,
  "paymentId": 300,
  "refundStatus": "COMPLETED",
  "refundAmount": 39000,
  "pointRefundAmount": 2500,
  "pgRefundAmount": 36500,
  "paymentStatus": "PARTIAL_REFUNDED",
  "orderStatus": "COMPLETED",
  "items": [
    {
      "refundItemId": 800,
      "orderItemId": 400,
      "productId": 10,
      "refundQuantity": 1,
      "refundAmount": 39000,
      "pointRefundAmount": 2500,
      "pgRefundAmount": 36500,
      "restoredStockQuantity": 1
    }
  ],
  "pointTransactions": [
    {
      "type": "USE_RESTORE",
      "amount": 2500
    },
    {
      "type": "EARN_CANCEL",
      "amount": 365
    }
  ],
  "refundedAt": "2026-05-29T19:00:00+09:00"
}
```

### 처리 규칙

- 환불 대상 주문 상품의 잔여 환불 가능 수량을 초과할 수 없습니다.
- 선검증 후 DB 트랜잭션에서 환불 기록, 환불 상품 기록, 재고 복구, 포인트 사용분 복구, 적립분 회수, 주문/결제 상태 갱신을 처리합니다.
- DB 트랜잭션 커밋 후 PortOne PG 취소 API를 호출합니다.
- PG 취소 성공 시 환불 상태를 `COMPLETED`로 갱신합니다.
- PG 취소 실패 시 환불 상태를 `FAILED`로 갱신하고 `REFUND_PG_CANCEL_FAILED`를 반환합니다. 운영에서는 실패 로그와 수동 보정 대상 추적이 필요합니다.
- 전액 환불이면 주문 상태는 `CANCELED`, 결제 상태는 `REFUNDED`입니다.
- 부분 환불이면 주문 상태는 `COMPLETED`를 유지하고 결제 상태만 `PARTIAL_REFUNDED`로 변경합니다.

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `VALIDATION_FAILED` | 400 | 사유 누락, 수량 1 미만 |
| `REFUND_ITEM_REQUIRED` | 400 | 환불할 주문 상품 목록 없음 |
| `ORDER_NOT_FOUND` | 404 | 주문 없음 |
| `ORDER_ACCESS_DENIED` | 403 | 타인의 주문 |
| `ORDER_ITEM_NOT_FOUND` | 404 | 주문 상품 없음 |
| `PAYMENT_NOT_FOUND` | 404 | 결제 없음 |
| `REFUND_NOT_ALLOWED` | 409 | 결제완료 또는 부분환불 상태가 아님 |
| `REFUND_QUANTITY_EXCEEDED` | 400 | 잔여 환불 가능 수량 초과 |
| `REFUND_PG_CANCEL_FAILED` | 502 | PortOne PG 취소 실패 |
