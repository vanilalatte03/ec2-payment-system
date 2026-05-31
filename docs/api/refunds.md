# 환불 API

- 환불 금액은 클라이언트가 입력하지 않습니다.
- 서버가 주문 상품의 가격 스냅샷과 환불 수량으로 자동 산정합니다.
- 부분 환불은 원 결제의 포인트/PG 결제 비율에 따라 분리합니다.
    - 포인트 환불 금액: `floor(환불금액 * 사용포인트 / 주문총액)`
    - PG 환불 금액: `환불금액 - 포인트환불금액`
- 마지막 전액 환불이 되는 요청은 소수점 버림 누적 오차를 없애기 위해 남은 포인트/PG 환불 가능액을 모두 배정합니다.

## 공통 Response

- 부분 환불과 전체 환불은 동일한 `RefundResponse` DTO를 사용합니다.
- `refundedAt`은 환불 완료 전 또는 실패 시 `null`입니다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `refundId` | `Long` | 생성된 환불 ID |
| `orderId` | `Long` | 환불 대상 주문 ID |
| `paymentId` | `Long` | 환불 대상 결제 ID |
| `refundStatus` | `RefundStatus` | `PROCESSING`, `COMPLETED`, `FAILED` |
| `refundAmount` | `Integer` | 총 환불 금액 |
| `pointRefundAmount` | `Integer` | 복구되는 포인트 금액 |
| `pgRefundAmount` | `Integer` | PG 환불 금액 |
| `reason` | `String` | 환불 사유 |
| `items` | `List&lt;RefundItemResponse&gt;` | 환불 상품 목록 |
| `createdAt` | `LocalDateTime` | 환불 요청 생성 시각 |
| `refundedAt` | `LocalDateTime` | PG 환불 완료 시각 |

```json
{
  "refundId": 5001,
  "orderId": 1,
  "paymentId": 3001,
  "refundStatus": "COMPLETED",
  "refundAmount": 10000,
  "pointRefundAmount": 2000,
  "pgRefundAmount": 8000,
  "reason": "일부 상품 환불 요청",
  "items": [
    {
      "refundItemId": 9001,
      "orderItemId": 101,
      "productName": "노트",
      "refundQuantity": 3,
      "unitPrice": 3000,
      "refundAmount": 9000,
      "pointRefundAmount": 1800,
      "pgRefundAmount": 7200
    },
    {
      "refundItemId": 9002,
      "orderItemId": 102,
      "productName": "연필",
      "refundQuantity": 1,
      "unitPrice": 1000,
      "refundAmount": 1000,
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
- HTTP Status: `201 Created`

### Path Variable

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `orderId` | `Long` | Y | 부분 환불할 주문 ID |

### Request Body

| 필드 | 타입                           | 필수 | 설명 |
|---|------------------------------|---|---|
| `reason` | `String`                     | Y | 환불 사유 |
| `items` | `List&lt<RefundItemRequest>` | Y | 환불할 주문 상품과 수량 목록 |
| `items[].orderItemId` | `Long`                       | Y | 주문 상품 ID |
| `items[].quantity` | `Integer`                    | Y | 환불 수량. 1 이상 |

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
- 공통 `RefundResponse` DTO를 사용합니다.
- 부분 환불의 경우 `items`에는 실제 환불 요청한 주문 상품만 포함됩니다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `refundId` | Long | 생성된 환불 ID |
| `orderId` | Long | 환불 대상 주문 ID |
| `paymentId` | Long | 환불 대상 결제 ID |
| `refundStatus` | RefundStatus | 환불 상태. `PENDING`, `PROCESSING`, `FAILED` |
| `refundAmount` | Integer | 총 환불 금액 |
| `pointRefundAmount` | Integer | 복구되는 포인트 금액 |
| `pgRefundAmount` | Integer | PG 환불 금액 |
| `reason` | String | 환불 사유 |
| `items` | List<RefundItemResponse> | 환불 상품 목록 |
| `createdAt` | LocalDateTime | 환불 요청 생성 시각 |
| `refundedAt` | LocalDateTime | PG 환불 완료 시각. 완료 전 또는 실패 시 `null` |

```json
{
  "refundId": 5001,
  "orderId": 1,
  "paymentId": 3001,
  "refundStatus": "COMPLETED",
  "refundAmount": 10000,
  "pointRefundAmount": 2000,
  "pgRefundAmount": 8000,
  "reason": "일부 상품 환불 요청",
  "items": [
    {
      "refundItemId": 9001,
      "orderItemId": 101,
      "productName": "노트",
      "refundQuantity": 3,
      "unitPrice": 3000,
      "refundAmount": 9000,
      "pointRefundAmount": 1800,
      "pgRefundAmount": 7200
    },
    {
      "refundItemId": 9002,
      "orderItemId": 102,
      "productName": "연필",
      "refundQuantity": 1,
      "unitPrice": 1000,
      "refundAmount": 1000,
      "pointRefundAmount": 200,
      "pgRefundAmount": 800
    }
  ],
  "createdAt": "2026-05-31T15:20:00",
  "refundedAt": "2026-05-31T15:20:05"
}
```

## 2. 전체 환불 요청

결제 완료된 결제 건에 대해 남은 환불 가능 금액 전체를 환불 요청합니다.

- Method: `POST`
- Path: `/api/payments/{paymentId}/refunds`
- 인증: 필요
- HTTP Status: `201 Created`

### Path Variable

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `paymentId` | `Long` | Y | 전체 환불할 결제 ID |

### Request Body

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `reason` | `String` | Y | 환불 사유 |

### Response Data

- 공통 `RefundResponse` DTO를 사용합니다.
- 전체 환불의 경우 `items`에는 남은 환불 가능 주문 상품 전체가 포함됩니다.

```json
{
  "refundId": 5002,
  "orderId": 1,
  "paymentId": 3001,
  "refundStatus": "COMPLETED",
  "refundAmount": 25000,
  "pointRefundAmount": 5000,
  "pgRefundAmount": 20000,
  "reason": "전체 주문 환불 요청",
  "items": [
    {
      "refundItemId": 9003,
      "orderItemId": 101,
      "productName": "노트",
      "refundQuantity": 5,
      "unitPrice": 3000,
      "refundAmount": 15000,
      "pointRefundAmount": 3000,
      "pgRefundAmount": 12000
    },
    {
      "refundItemId": 9004,
      "orderItemId": 102,
      "productName": "연필",
      "refundQuantity": 2,
      "unitPrice": 1000,
      "refundAmount": 2000,
      "pointRefundAmount": 400,
      "pgRefundAmount": 1600
    },
    {
      "refundItemId": 9005,
      "orderItemId": 103,
      "productName": "필통",
      "refundQuantity": 1,
      "unitPrice": 8000,
      "refundAmount": 8000,
      "pointRefundAmount": 1600,
      "pgRefundAmount": 6400
    }
  ],
  "createdAt": "2026-05-31T15:30:00",
  "refundedAt": "2026-05-31T15:30:05"
}
```

### 처리 규칙

1. 환불 대상 주문 상품의 잔여 환불 가능 수량을 초과할 수 없습니다.

2. 선검증 후 DB 트랜잭션에서 다음 작업을 처리합니다.
    - 환불 기록 생성: `Refund.status = PROCESSING`
    - 환불 상품 기록 생성
    - 재고 복구
    - 포인트 사용분 복구
    - 적립분 회수

3. DB 트랜잭션 커밋 후 PortOne PG 취소 API를 호출합니다.

4. PG 취소 성공 시 다음 작업을 처리합니다.
    - 환불 상태를 `COMPLETED`로 갱신합니다.
    - 전액 환불이면 주문 상태를 `CANCELED`, 결제 상태를 `FULL_REFUNDED`로 변경합니다.
    - 부분 환불이면 주문 상태는 `COMPLETED`를 유지하고 결제 상태만 `PARTIAL_REFUNDED`로 변경합니다.

5. PG 취소 실패 시 환불 상태를 `FAILED`로 갱신하고 `REFUND_PG_CANCEL_FAILED`를 반환합니다.
    - 운영에서는 실패 로그와 수동 보정 대상 추적이 필요합니다.

### Errors

| 코드 | HTTP | 발생 조건 |
|---|---|---|
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `VALIDATION_FAILED` | 400 | 사유 누락, 수량 1 미만 |
| `REFUND_ITEM_REQUIRED` | 400 | 환불할 주문 상품 목록 없음 |
| `ORDER_NOT_FOUND` | 404 | 주문 없음 |
| `ORDER_ACCESS_DENIED` | 403 | 타인의 주문 |
| `ORDER_ITEM_NOT_FOUND` | 404 | 주문 상품 없음 |
| `PAYMENT_NOT_FOUND` | 404 | 결제 없음 |
| `REFUND_NOT_ALLOWED` | 409 | 결제가 환불 가능한 상태가 아님 |
| `REFUND_QUANTITY_EXCEEDED` | 400 | 잔여 환불 가능 수량 초과 |
| `REFUND_PG_CANCEL_FAILED` | 502 | PortOne PG 취소 실패 |
