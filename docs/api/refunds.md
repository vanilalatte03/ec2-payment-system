# 환불 API

환불 금액은 클라이언트가 직접 입력하지 않습니다. 서버가 주문 상품 가격, 환불 수량, 결제 당시 포인트/PG 비율, 적립 포인트 회수 금액을 기준으로 계산합니다.

## 공통 응답 DTO

부분 환불과 전체 환불은 동일한 `RefundResponse` DTO를 사용합니다.

### RefundResponse

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `refundId` | `Long` | 생성된 환불 ID |
| `refundStatus` | `RefundStatus` | `PROCESSING`, `COMPLETED`, `FAILED`, `PG_RESULT_UNKNOWN` |
| `actualRefundAmount` | `Long` | 적립 포인트 회수 후 고객에게 실제 반환되는 최종 환불 금액 |
| `pointRefundAmount` | `Long` | 고객에게 실제 복구되는 사용 포인트 금액 |
| `pgRefundAmount` | `Long` | PG사를 통해 실제 환불되는 금액 |
| `reason` | `String` | 환불 사유 |
| `items` | `List<RefundItemResponse>` | 환불 상품 목록 |
| `createdAt` | `LocalDateTime` | 환불 요청 생성 시각 |
| `refundedAt` | `LocalDateTime` | PG 환불 완료 시각. 완료 전 또는 실패 시 `null` |

### RefundItemResponse

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `refundItemId` | `Long` | 환불 상품 상세 ID |
| `orderItemId` | `Long` | 주문 상품 ID |
| `productName` | `String` | 상품명 |
| `refundQuantity` | `Integer` | 환불 수량 |
| `unitPrice` | `Long` | 환불 당시 상품 단가 |
| `grossRefundAmount` | `Long` | 상품 가격 * 환불 수량 기준 금액. 적립 포인트 회수 전 상품 기준 금액 |
| `actualRefundAmount` | `Long` | 이 상품에 배분된 실제 반환 금액. `pointRefundAmount + pgRefundAmount` |
| `pointRefundAmount` | `Long` | 이 상품에 배분된 실제 포인트 반환액 |
| `pgRefundAmount` | `Long` | 이 상품에 배분된 실제 PG 환불액 |

> `RefundResponse.actualRefundAmount`는 전체 실제 반환액이고, `RefundItemResponse.grossRefundAmount`는 상품 기준 금액입니다. 적립 포인트 회수가 발생하면 상품별 `grossRefundAmount` 합계와 전체 `actualRefundAmount`가 다를 수 있습니다.

```json
{
  "refundId": 5001,
  "refundStatus": "COMPLETED",
  "actualRefundAmount": 10000,
  "pointRefundAmount": 2000,
  "pgRefundAmount": 8000,
  "reason": "일부 상품 환불 요청",
  "items": [
    {
      "refundItemId": 9001,
      "orderItemId": 101,
      "productName": "셔츠",
      "refundQuantity": 3,
      "unitPrice": 3000,
      "grossRefundAmount": 9000,
      "actualRefundAmount": 9000,
      "pointRefundAmount": 1800,
      "pgRefundAmount": 7200
    },
    {
      "refundItemId": 9002,
      "orderItemId": 102,
      "productName": "양말",
      "refundQuantity": 1,
      "unitPrice": 1000,
      "grossRefundAmount": 1000,
      "actualRefundAmount": 1000,
      "pointRefundAmount": 200,
      "pgRefundAmount": 800
    }
  ],
  "createdAt": "2026-05-31T15:20:00",
  "refundedAt": "2026-05-31T15:20:05"
}
```

## 1. 부분 환불 요청

결제 완료된 주문에 대해 일부 주문 상품 또는 일부 수량 환불을 요청합니다.

- Method: `POST`
- Path: `/api/orders/{orderId}/refunds`
- 인증: 필요
- Header: `Idempotency-Key` 필수
- HTTP Status: `201 Created`

### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `orderId` | `Long` | Y | 부분 환불할 주문 ID |

### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reason` | `String` | Y | 환불 사유 |
| `items` | `List<RefundItemRequest>` | Y | 환불할 주문 상품과 수량 목록 |
| `items[].orderItemId` | `Long` | Y | 주문 상품 ID |
| `items[].quantity` | `Integer` | Y | 환불 수량. 1 이상 |

```json
{
  "reason": "일부 상품 환불 요청",
  "items": [
    {
      "orderItemId": 101,
      "quantity": 3
    },
    {
      "orderItemId": 102,
      "quantity": 1
    }
  ]
}
```

### Response Data

공통 `RefundResponse` DTO를 사용합니다. 부분 환불의 경우 `items`에는 실제 환불 요청한 주문 상품만 포함됩니다.

## 2. 전체 환불 요청

결제 완료된 결제 건에 대해 남은 환불 가능 금액 전체를 환불 요청합니다.

- Method: `POST`
- Path: `/api/payments/{paymentId}/refunds`
- 인증: 필요
- Header: `Idempotency-Key` 필수
- HTTP Status: `201 Created`

### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `paymentId` | `Long` | Y | 전체 환불할 결제 ID |

### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reason` | `String` | Y | 환불 사유 |

```json
{
  "reason": "전체 주문 환불 요청"
}
```

### Response Data

공통 `RefundResponse` DTO를 사용합니다. 전체 환불의 경우 `items`에는 남은 환불 가능 주문 상품 전체가 포함됩니다.

## 3. 환불 금액 계산 정책

- 상품별 `grossRefundAmount`는 `상품 단가 * 환불 수량`입니다.
- 전체 `actualRefundAmount`는 `pointRefundAmount + pgRefundAmount`입니다.
- 적립 포인트 회수가 발생하면 `actualRefundAmount`는 상품별 `grossRefundAmount` 합계보다 작을 수 있습니다.
- 상품별 실제 반환액은 각 상품의 gross 금액을 초과하지 않도록 배분합니다.
- 마지막 전체 환불에서는 이전 부분 환불에서 발생한 비율 계산 오차를 보정하기 위해 남은 gross 포인트/PG 금액을 기준으로 계산합니다.

## 4. PortOne 결제 취소 API

`pgRefundAmount > 0`이면 백엔드 서버가 PortOne V2 결제 취소 API를 호출합니다.

클라이언트는 PortOne API를 직접 호출하지 않습니다. PortOne API Secret이 노출되지 않도록 반드시 백엔드 서버에서 호출해야 합니다.

- Method: `POST`
- URL: `https://api.portone.io/payments/{portonePaymentId}/cancel`
- 인증 헤더: `Authorization: PortOne {API_SECRET}`
- Content-Type: `application/json`

부분 환불 요청 예시:

```json
{
  "reason": "일부 상품 환불 요청",
  "amount": 8000,
  "currentCancellableAmount": 20000
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reason` | `String` | Y | 환불 사유 |
| `amount` | `Long` | 부분 환불 시 Y | 이번에 취소할 PG 결제 금액. `pgRefundAmount`를 전달합니다. |
| `currentCancellableAmount` | `Long` | 부분 환불 시 Y | 취소 요청 직전의 PG 환불 가능 금액입니다. 실제 금액과 다르면 PortOne이 취소를 거절할 수 있습니다. |

포인트 전액 결제로 `pgRefundAmount == 0`이면 PortOne API 호출을 생략하고 내부 환불 확정 단계로 이동합니다.

## 5. 처리 규칙

- 동일 결제에 대해 `PROCESSING` 또는 `PG_RESULT_UNKNOWN` 상태 환불이 있으면 새 환불 요청을 막습니다.
- 환불 요청은 `PROCESSING` 상태로 저장한 뒤, outbox를 통해 PG 취소를 비동기로 처리합니다.
- PG 취소 성공 후 주문, 결제, 포인트, 재고, 환불 상태를 확정합니다.
- 명확한 PG 취소 실패는 `FAILED`로 변경합니다.
- 네트워크 오류처럼 PG 결과를 확정할 수 없으면 `PG_RESULT_UNKNOWN`으로 변경하고 재시도합니다.
- 같은 `Idempotency-Key`와 같은 요청 내용은 기존 환불 응답을 반환합니다.
- 같은 `Idempotency-Key`지만 요청 내용이 다르면 충돌로 처리합니다.

## 6. 환불 수량 예약 정책

- 환불 요청이 생성되면 서버는 먼저 `order_items.refund_reserved_quantity`를 증가시킵니다.
- `getRemainingRefundableQuantity()`는 `refunded_quantity`와 `refund_reserved_quantity`를 모두 제외하고 계산합니다.
- 환불이 완료되면 예약 수량은 `refunded_quantity`로 이동하고 상품 재고를 복구합니다.
- 환불 실패가 확정되면 예약 수량을 해제합니다.
- `PG_RESULT_UNKNOWN`은 이후 재조회 결과에 따라 `COMPLETED` 또는 `FAILED`로 확정됩니다.

## Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `VALIDATION_FAILED` | 400 | 사유 누락, 수량 1 미만, 환불 금액 계산 불일치 |
| `REFUND_ITEM_REQUIRED` | 400 | 환불할 주문 상품 목록 없음 |
| `ORDER_NOT_FOUND` | 404 | 주문 없음 |
| `ORDER_ACCESS_DENIED` | 403 | 타인의 주문 |
| `ORDER_ITEM_NOT_FOUND` | 404 | 주문 상품 없음 |
| `PAYMENT_NOT_FOUND` | 404 | 결제 없음 |
| `REFUND_NOT_ALLOWED` | 409 | 결제가 환불 가능한 상태가 아님 |
| `REFUND_QUANTITY_EXCEEDED` | 400 | 남은 환불 가능 수량 초과 |
| `REFUND_PG_CANCEL_FAILED` | 502 | PortOne PG 취소 실패 |
| `REFUND_IN_PROGRESS` | 409 | 동일 결제의 환불 처리 진행 중 |
