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
| `orderId` | `Long` | 환불 대상 주문 ID                         |
| `paymentId` | `Long` | 환불 대상 결제 ID                         |
| `refundStatus` | `RefundStatus` | `PROCESSING`, `COMPLETED`, `FAILED` |
| `refundAmount` | `Integer` | 총 환불 금액                             |
| `pointRefundAmount` | `Integer` | 복구되는 포인트 금액                         |
| `pgRefundAmount` | `Integer` | PG 환불 금액                            |
| `reason` | `String` | 환불 사유                               |
| `items` | `List&lt;RefundItemResponse&gt;` | 환불 상품 목록                            |
| `createdAt` | `LocalDateTime` | 환불 요청 생성 시각                         |
| `refundedAt` | `LocalDateTime` | PG 환불 완료 시각                         |

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
| `orderId` | Long | 환불 대상 주문 ID                                |
| `paymentId` | Long | 환불 대상 결제 ID                                |
| `refundStatus` | RefundStatus | 환불 상태. `PROCESSING`, `COMPLETED`, `FAILED` |
| `refundAmount` | Integer | 총 환불 금액                                    |
| `pointRefundAmount` | Integer | 복구되는 포인트 금액                                |
| `pgRefundAmount` | Integer | PG 환불 금액                                   |
| `reason` | String | 환불 사유                                      |
| `items` | List<RefundItemResponse> | 환불 상품 목록                                   |
| `createdAt` | LocalDateTime | 환불 요청 생성 시각                                |
| `refundedAt` | LocalDateTime | PG 환불 완료 시각. 완료 전 또는 실패 시 `null`           |

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


## 처리 규칙

#### 1. 공통 원칙

- 환불 금액은 클라이언트가 전달하지 않습니다.
- 서버가 주문 상품의 가격 스냅샷과 환불 수량을 기준으로 계산합니다.
- 환불 상태는 `PROCESSING`, `COMPLETED`, `FAILED`만 사용합니다.
- 동일한 결제에 여러 환불 요청이 동시에 들어와도 환불 가능 금액과 수량을 초과할 수 없습니다.

#### 2. 요청 검증 및 동시 환불 방지

서버는 환불 처리 전에 다음 항목을 검증합니다.

1. 인증된 사용자가 해당 주문 또는 결제의 소유자인지 확인합니다.
2. 결제 상태가 `COMPLETED` 또는 `PARTIAL_REFUNDED`인지 확인합니다.
3. 부분 환불 요청이라면 `items`가 비어 있지 않은지 확인합니다.
4. 요청한 주문 상품이 환불 대상 주문에 포함되는지 확인합니다.
5. 환불 수량이 1 이상인지 확인합니다.
6. 기존 `COMPLETED` 환불 수량을 제외한 잔여 수량을 초과하지 않는지 확인합니다.
7. 기존 `COMPLETED` 환불 금액을 제외한 잔여 환불 가능 금액을 초과하지 않는지 확인합니다.

동일한 결제 건의 환불은 한 번에 하나만 처리합니다.

동일한 결제에 `PROCESSING` 상태의 환불이 이미 존재하면 새로운 환불 요청을 처리하지 않고 `REFUND_IN_PROGRESS` 에러를 반환합니다.

동시 요청이 거의 같은 시점에 들어오더라도 중복 환불이 생성되지 않도록 환불 요청 등록 단계에서 결제 레코드 하나에 DB 쓰기 잠금(`PESSIMISTIC_WRITE`)을 적용합니다.

```text
DB 트랜잭션 시작
→ 결제 레코드 DB 쓰기 잠금 획득
→ 기존 PROCESSING 환불 존재 여부 확인
→ 존재하면 REFUND_IN_PROGRESS 반환
→ 없으면 환불 가능 수량과 금액 검증
→ PROCESSING 상태의 환불 레코드 저장
→ DB 트랜잭션 커밋
→ DB 쓰기 잠금 자동 해제
```

PortOne 결제 취소 API를 호출하는 동안에는 DB 쓰기 잠금을 유지하지 않습니다.

#### 3. 환불 금액 계산

부분 환불 금액은 다음과 같이 계산합니다.

```text
상품별 환불 금액 = 주문 당시 상품 단가 * 환불 수량

총 환불 금액 = 상품별 환불 금액의 합계

포인트 환불 금액 =
floor(총 환불 금액 * 원 결제 사용 포인트 / 주문 총액)

PG 환불 금액 =
총 환불 금액 - 포인트 환불 금액
```

마지막 환불 요청으로 남은 상품이 모두 환불되는 경우에는 소수점 버림 누적 오차를 제거합니다.

```text
마지막 포인트 환불 금액 =
원 결제 사용 포인트 - 기존 포인트 환불 금액 합계

마지막 PG 환불 금액 =
원 PG 결제 금액 - 기존 PG 환불 금액 합계
```

`refund_item`에도 상품별 포인트 환불 금액과 PG 환불 금액을 저장합니다.

상품별 금액 배분 시 발생하는 버림 오차는 `orderItemId` 오름차순 기준 마지막 상품에 반영합니다.

다음 조건을 항상 만족해야 합니다.

```text
refund.refund_amount
= refund.point_refund_amount + refund.pg_refund_amount

refund.refund_amount
= refund_item.refund_amount 합계

refund.point_refund_amount
= refund_item.point_refund_amount 합계

refund.pg_refund_amount
= refund_item.pg_refund_amount 합계
```

#### 4. 환불 요청 등록

첫 번째 DB 트랜잭션에서는 환불 요청만 등록합니다.

1. 환불 레코드를 `PROCESSING` 상태로 생성합니다.
2. 환불 상품 레코드를 생성합니다.
3. 환불 가능 수량과 금액을 예약합니다.

이 단계에서는 아직 다음 작업을 수행하지 않습니다.

- 재고 복구
- 사용 포인트 복구
- 적립 포인트 회수
- 주문 상태 변경
- 결제 상태 변경

`PROCESSING` 상태의 환불 수량과 금액도 잔여 환불 가능 수량 및 금액 계산에서 제외합니다.

#### 5. PG 취소 처리

`pgRefundAmount > 0`이면 DB 트랜잭션 커밋 후 PortOne V2 결제 취소 API를 호출합니다.

```text
pgRefundAmount > 0  -> PortOne PG 취소 API 호출
pgRefundAmount == 0 -> PG 호출 생략
```

포인트 전액 결제인 `POINT_ONLY` 환불은 PG 호출 없이 내부 환불 확정 단계로 이동합니다.

외부 API와 DB는 하나의 트랜잭션으로 묶을 수 없으므로 환불 요청 ID를 기준으로 중복 호출을 방지해야 합니다.

반드시 PortOne 취소 요청에도 `refundId` 기반의 멱등 키를 전달합니다.

#### 6. 환불 성공 확정

PG 취소 성공 또는 포인트 전액 결제 환불이면 두 번째 DB 트랜잭션에서 다음 작업을 처리합니다.

1. 환불 상태를 `COMPLETED`로 변경합니다.
2. 환불 상품 수량만큼 재고를 복구합니다.
3. 사용 포인트를 복구합니다.
4. 적립 포인트를 회수합니다. 적립 포인트 회수 금액은 이번 PG 환불 금액을 기준으로 계산합니다.
```text
적립 포인트 회수 금액 = floor(PG 환불 금액 * 0.01)
```
5. 포인트 잔액 스냅샷인 `user.point_balance`을 갱신합니다.
6. 포인트 원장인 `point_transaction`에 거래 이력을 기록합니다.
7. 주문 및 결제 상태를 갱신합니다.
8. `refundedAt`에 환불 완료 시각을 저장합니다.

포인트 원장에는 환불 원인을 추적할 수 있도록 `refund_id`를 저장합니다.

```text
USE_RESTORE -> 환불에 따른 사용 포인트 복구
EARN_CANCEL -> 환불에 따른 적립 포인트 회수
```

적립 포인트 회수 후 잔액이 음수가 되는 것은 프로젝트의 기존 정책에 따라 허용합니다.

#### 7. 주문 및 결제 상태 변경

일부 수량 또는 일부 상품만 환불된 경우:

```text
Order.status   = COMPLETED
Payment.status = PARTIAL_REFUNDED
```

남은 환불 가능 수량과 금액이 모두 0인 경우:

```text
Order.status   = CANCELED
Payment.status = REFUNDED
```

#### 8. PG 취소 요청 실패 처리

PortOne API 호출 결과에 따라 처리 방식을 구분합니다.

##### 8.1. 취소 거절이 명확한 경우

PortOne이 요청값 오류, 취소 가능 금액 불일치 등 명확한 실패 응답을 반환하면 별도 DB 트랜잭션에서 다음 작업을 처리합니다.

1. 환불 상태를 `FAILED`로 변경합니다.
2. 실패 사유를 로그에 기록합니다.
3. `REFUND_PG_CANCEL_FAILED` 에러를 반환합니다.
4. 재고, 포인트, 주문 상태, 결제 상태는 변경하지 않습니다.

##### 8.2. 처리 결과를 확정할 수 없는 경우

네트워크 연결 오류, 응답 타임아웃, PortOne 서버 오류가 발생하면 PG 취소 실패로 단정할 수 없습니다.

PortOne에서는 취소가 완료되었지만 우리 서버가 응답을 받지 못했을 수 있기 때문입니다.

이 경우 다음 규칙을 적용합니다.

1. 환불 상태를 `PROCESSING`으로 유지합니다.
2. 재고, 포인트, 주문 상태, 결제 상태는 아직 변경하지 않습니다.
3. 동일한 환불 건으로 새로운 환불 레코드를 생성하지 않습니다.
4. 최초 요청과 동일한 멱등 키로 PortOne 취소 API를 다시 호출합니다.

```http
Idempotency-Key: "refund-cancel-request-{refundId}"
```

5. PortOne 취소 성공이 확인되면 내부 환불 확정 단계로 이동합니다.
6. 일정 횟수 이상 재시도해도 결과를 확인할 수 없으면 운영 확인 대상으로 기록합니다.

동일한 멱등 키를 사용하면 네트워크 오류로 요청을 다시 보내더라도 같은 PG 취소가 중복 처리되는 것을 방지할 수 있습니다.

#### 9. PG 취소 성공 후 내부 처리 실패

PG 취소는 성공했지만 내부 DB 반영이 실패할 수 있습니다.

이 경우 환불 상태는 `PROCESSING`으로 유지하고 운영 재처리 대상으로 기록합니다.

재처리 작업은 다음 조건을 만족해야 합니다.

- PortOne 취소 결과를 다시 조회합니다.
  내부 환불 확정 시 환불 레코드에 DB 쓰기 잠금을 적용합니다.
  이미 `COMPLETED` 상태이면 재고와 포인트를 다시 변경하지 않고 기존 결과를 반환합니다.

- 포인트 원장의 중복 저장을 방지하기 위해 `point_transaction`에는 `(refund_id, type)` UNIQUE 제약을 적용합니다.
- 이미 PG 취소가 완료되었다면 PortOne V2 결제 취소 API를 다시 호출하지 않습니다.
- 내부 환불 확정 작업만 다시 수행합니다.
- 재고와 포인트 원장이 중복 반영되지 않도록 `refundId` 기준으로 멱등하게 처리합니다.

운영 환경에서는 `PROCESSING` 상태가 일정 시간 이상 유지되는 환불을 조회하여 재처리하거나 관리자에게 알림을 보내야 합니다.

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
