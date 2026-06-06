# 환불 API

- 환불 금액은 클라이언트가 입력하지 않습니다.
- 서버가 주문 상품의 가격 스냅샷과 환불 수량으로 자동 산정합니다.
- 부분 환불은 원 결제의 포인트/PG 결제 비율에 따라 분리합니다.
    - 포인트 환불 금액: `floor(환불금액 * 사용포인트 / 주문총액)`
    - PG 환불 금액: `환불금액 - 포인트환불금액`
- 마지막 전액 환불이 되는 요청은 소수점 버림 누적 오차를 없애기 위해 남은 포인트/PG 환불 가능액을 모두 배정합니다.
    - 마지막 적립 포인트 회수 금액 = 원 결제 적립 포인트 - 기존 적립 포인트 회수 합계

## 공통 Response

- 부분 환불과 전체 환불은 동일한 `RefundResponse` DTO를 사용합니다.
- `refundedAt`은 환불 완료 전 또는 실패 시 `null`입니다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `refundId` | `Long` | 생성된 환불 ID                           |
| `refundStatus` | `RefundStatus` | `PROCESSING`, `COMPLETED`, `FAILED`, `PG_RESULT_UNKNOWN` |
| `refundAmount` | `Long` | 총 환불 금액                             |
| `pointRefundAmount` | `Long` | 복구되는 포인트 금액                         |
| `pgRefundAmount` | `Long` | PG 환불 금액                            |
| `reason` | `String` | 환불 사유                               |
| `items` | `List&lt;RefundItemResponse&gt;` | 환불 상품 목록                            |
| `createdAt` | `LocalDateTime` | 환불 요청 생성 시각                         |
| `refundedAt` | `LocalDateTime` | PG 환불 완료 시각                         |

```json
{
  "refundId": 5001,
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
- Header: `Idempotency-Key` 필수
- HTTP Status: `201 Created`

### Path Variable

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `orderId` | `Long` | Y | 부분 환불할 주문 ID |

### Request Body

| 필드 | 타입                           | 필수 | 설명 |
|---|------------------------------|---|---|
| `reason` | `String`                     | Y | 환불 사유 |
| `items` | `List&lt;RefundItemRequest&gt;` | Y | 환불할 주문 상품과 수량 목록 |
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

| 필드 | 타입 | 설명                                         |
|---|---|--------------------------------------------|
| `refundId` | Long | 생성된 환불 ID                                  |
| `refundStatus` | RefundStatus | 환불 상태. `PROCESSING`, `COMPLETED`, `FAILED`, `PG_RESULT_UNKNOWN` |
| `refundAmount` | Long | 총 환불 금액                                    |
| `pointRefundAmount` | Long | 복구되는 포인트 금액                                |
| `pgRefundAmount` | Long | PG 환불 금액                                   |
| `reason` | String | 환불 사유                                      |
| `items` | List<RefundItemResponse> | 환불 상품 목록                                   |
| `createdAt` | LocalDateTime | 환불 요청 생성 시각                                |
| `refundedAt` | LocalDateTime | PG 환불 완료 시각. 완료 전 또는 실패 시 `null`           |

```json
{
  "refundId": 5001,
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
- Header: `Idempotency-Key` 필수
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

## 3. PortOne 결제 취소 API

`pgRefundAmount > 0`이면 백엔드 서버가 PortOne V2 결제 취소 API를 호출합니다.

이 API는 클라이언트가 직접 호출하지 않습니다.
PortOne API Secret이 외부에 노출되지 않도록 반드시 백엔드 서버에서 호출해야 합니다.

- Method: `POST`
- URL: `https://api.portone.io/payments/{portonePaymentId}/cancel`
- 인증 헤더: `Authorization: PortOne {API_SECRET}`
- Content-Type: `application/json`

`portonePaymentId`는 우리 DB의 `payment.id`가 아닙니다.

결제 생성 시 발급하여 저장한 `payment.portone_payment_id`입니다.

부분 환불 요청 예시:

```json
{
  "reason": "일부 상품 환불 요청",
  "amount": 8000,
  "currentCancellableAmount": 20000
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `reason` | `String` | Y | 환불 사유 |
| `amount` | `Long` | 부분 환불 시 Y | 이번에 취소할 PG 결제 금액. `pgRefundAmount`를 전달합니다. |
| `currentCancellableAmount` | `Long` | 부분 환불 시 Y | 취소 요청 직전의 PG 환불 가능 잔액입니다. 실제 잔액과 다르면 PortOne이 취소를 거부합니다. |

전체 환불은 남은 PG 결제 금액 전체를 취소합니다.

포인트 전액 결제로 `pgRefundAmount == 0`이면 PortOne API 호출을 생략하고 내부 환불 확정 단계로 이동합니다.

네트워크 오류로 동일한 요청을 다시 보낼 수 있으므로 요청 헤더에 고유한 멱등 키를 포함합니다.

```http
Idempotency-Key: "refund-cancel-request-{refundId}"
```

- 멱등 키는 동일한 취소 요청이 중복 처리되지 않도록 식별하는 값입니다.

#### 처리 규칙

- 환불 금액은 서버가 주문 당시 가격과 수량으로 계산한다.
- 잔여 수량·금액 계산 시 `PROCESSING`, `COMPLETED` 환불을 제외한다.
- 마지막 전체 환불에서는 포인트 배분의 누적 오차를 보정한다.
- 동일 결제의 환불은 한 번에 하나만 처리한다.
- 동시 환불을 방지하기 위해 우리 DB의 원본 결제 정보(`payment`)에 `PESSIMISTIC_WRITE` 잠금을 적용한다.
- 기존 `PROCESSING` 환불이 있으면 `REFUND_IN_PROGRESS`를 반환한다.
- 환불 요청을 `PROCESSING`으로 저장하고 트랜잭션을 종료한 후 PG 취소를 요청한다.
- 포인트 전액 결제는 PG 호출을 생략한다.
- PG 취소 성공 시 재고, 포인트, 주문·결제 상태를 반영한다.
- 부분 환불은 `PARTIAL_REFUNDED`, 전체 환불은 `FULL_REFUNDED`로 처리한다.
- 명확한 PG 취소 실패는 `FAILED`로 변경한다.
- 타임아웃·네트워크 오류는 `PROCESSING`을 유지하고 재시도한다.
- 재시도 시 새 환불을 생성하지 않고 동일한 멱등 키를 사용한다.

`Idempotency-Key: refund-cancel-request-{refundId}`

### 환불 수량 예약 정책

- 환불 요청이 생성되면 서버는 먼저 `order_items.refund_reserved_quantity`를 증가시킵니다.
- `getRemainingRefundableQuantity()`는 `refunded_quantity`와 `refund_reserved_quantity`를 모두 제외하고 계산합니다.
- 환불이 완료되면 예약 수량을 `refunded_quantity`로 이동하고 상품 재고를 복구합니다.
- 환불 실패가 확정되면 예약 수량을 해제합니다.
- 타임아웃이나 네트워크 오류로 PortOne 취소 결과를 확정할 수 없으면 환불 상태를 `PG_RESULT_UNKNOWN`으로 변경하고, 재시도/대사 전까지 예약 수량은 유지합니다.
- `PG_RESULT_UNKNOWN`은 이후 재조회 결과에 따라 `COMPLETED` 또는 `FAILED`로 확정합니다.

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
| `REFUND_IN_PROGRESS` | 409 | 동일 결제의 환불 처리 진행 중 |
