# 주문 API

주문은 결제와 주문 상태 변경의 기준 단위입니다. 현재 구현된 API는 주문서 미리보기, 내 주문 내역 조회, 주문 상세 조회, 주문/결제 생성, 결제대기 주문 취소를 제공합니다.

## 주문서 미리보기

결제 직전 단계에서 장바구니에 담긴 상품을 주문서 형태로 변환해 보여줍니다. 주문 스냅샷을 저장하기 전이므로 상품의 현재 이름, 현재가, 재고 상태를 실시간으로 반영합니다.

- Method: `GET`
- Path: `/api/orders/preview`
- 인증: 필요
- HTTP Status: `200 OK`

### Query Parameters

쿼리 파라미터는 생략할 수 있습니다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `cartItemIds` | Long[] | N | 미리보기할 장바구니 상품 ID 목록. `null` 또는 빈 배열이면 장바구니 전체 |

```http
GET /api/orders/preview?cartItemIds=100&cartItemIds=101
```

### Response Data

```json
{
  "items": [
    {
      "cartItemId": 100,
      "productId": 10,
      "productName": "무선 키보드",
      "quantity": 2,
      "unitPrice": 39000,
      "lineAmount": 78000,
      "stock": 10,
      "status": "ON_SALE"
    }
  ],
  "totalQuantity": 2,
  "totalAmount": 78000
}
```

### 처리 규칙

- 주문, 주문상품, 결제 레코드를 생성하지 않는 읽기 전용 API입니다.
- `cartItemIds`가 없으면 장바구니 전체 상품을 주문서 미리보기 대상으로 봅니다.
- `cartItemIds`가 있으면 선택된 장바구니 상품만 주문서 미리보기 대상으로 봅니다.
- 같은 장바구니 상품 ID가 여러 번 들어와도 한 번만 미리보기 대상으로 처리합니다.
- 상품명, 가격, 재고, 판매 상태는 장바구니에 담긴 시점이 아니라 현재 상품 정보를 기준으로 응답합니다.
- 판매중이 아니거나 재고가 부족한 상품은 결제 화면으로 넘어갈 수 없도록 실패 응답을 반환합니다.

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `CART_EMPTY` | 400 | 미리보기 대상 장바구니 상품 없음 |
| `CART_ITEM_NOT_FOUND` | 404 | 선택한 장바구니 상품 없음 |
| `PRODUCT_NOT_ON_SALE` | 400 | 판매중 상품이 아님 |
| `ORDER_STOCK_SHORTAGE` | 409 | 현재 재고가 장바구니 수량보다 부족함 |

## 주문/결제 생성

주문서에서 결제하기를 누른 시점에 주문과 결제를 동시에 생성합니다. 단일 트랜잭션 안에서 재고를 검증하고 선차감합니다. 하나라도 재고가 부족하면 전체 주문 생성을 롤백합니다.

- Method: `POST`
- Path: `/api/orders`
- 인증: 필요
- HTTP Status: `201 Created`

### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `cartItemIds` | Long[] | N | 주문할 장바구니 상품 ID 목록. `null` 또는 빈 배열이면 장바구니 전체 |
| `usedPointAmount` | Long | Y | 사용할 포인트 금액. 0 이상 |

```json
{
  "cartItemIds": [100, 101],
  "usedPointAmount": 5000
}
```

### Response Data

```json
{
  "order": {
    "orderId": 200,
    "orderNumber": "ORD-20260529-000001",
    "status": "PAYMENT_PENDING",
    "totalAmount": 78000,
    "items": [
      {
        "orderItemId": 400,
        "productId": 10,
        "productName": "무선 키보드",
        "quantity": 2,
        "refundedQuantity": 0,
        "unitPrice": 39000,
        "lineAmount": 78000
      }
    ]
  },
  "payment": {
    "paymentId": 300,
    "portonePaymentId": "pay_4eb1c6ef-1d3b-4ee0-b48a-3ed567f9a0e7",
    "status": "PENDING",
    "type": "POINT_CARD",
    "usedPointAmount": 5000,
    "pgAmount": 73000
  },
  "nextAction": "OPEN_PORTONE_PAYMENT",
  "message": "주문과 결제 정보가 생성되었습니다."
}
```

`payment.pgAmount`가 `0`이면 `payment.type`은 `POINT_ONLY`, `nextAction`은 `CONFIRM_POINT_ONLY`로 내려갑니다. 이 경우 클라이언트는 PortOne 결제창을 열지 않고 결제 확정 API를 호출합니다.

### 처리 규칙

- 주문 상품에는 주문 생성 시점의 상품명과 가격 스냅샷을 저장합니다.
- 결제 레코드는 `PENDING` 상태로 함께 생성합니다.
- 서버가 PortOne에 전달할 `portonePaymentId`를 `pay_` + UUID 형식으로 미리 생성하고 결제 레코드에 저장합니다.
- 주문 생성 시점에 재고를 먼저 차감합니다.
- 포인트를 사용하는 주문은 주문 생성 시점에 포인트를 예약 차감하고 `USE_RESERVE` 원장을 생성합니다.
- 주문 생성 시점에는 장바구니 상품을 삭제하지 않습니다.

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `INVALID_USED_POINT` | 400 | 사용할 포인트가 음수이거나 주문 금액 초과 |
| `CART_EMPTY` | 400 | 주문 대상 장바구니 상품 없음 |
| `CART_ITEM_NOT_FOUND` | 404 | 선택한 장바구니 상품 없음 |
| `PRODUCT_NOT_ON_SALE` | 400 | 판매중 상품이 아님 |
| `ORDER_STOCK_SHORTAGE` | 409 | 재고 검증/차감 중 재고 부족 |
| `INSUFFICIENT_POINT` | 400 | 포인트 잔액 부족 |

## 내 주문 내역 조회

인증된 회원 본인의 주문 목록을 최신순으로 조회합니다. 각 주문의 주문번호, 주문 상태, 총액, 주문일 같은 목록 화면용 기본 정보를 반환합니다.

- Method: `GET`
- Path: `/api/orders`
- 인증: 필요
- HTTP Status: `200 OK`

### Response Data

```json
{
  "orders": [
    {
      "orderId": 200,
      "orderNumber": "ORD-20260529-000001",
      "status": "PAYMENT_PENDING",
      "totalAmount": 78000,
      "orderedAt": "2026-05-29T18:30:00"
    }
  ]
}
```

주문 내역이 없으면 빈 배열을 반환합니다.

```json
{
  "orders": []
}
```

### 처리 규칙

- 토큰의 회원 ID를 기준으로 본인 주문만 조회합니다.
- `orderedAt`은 주문 생성 시각입니다.
- 정렬은 `orderedAt` 최신순이며, 시간이 같으면 `orderId`가 큰 주문이 먼저 내려갑니다.

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |

## 주문 상세 조회

특정 주문의 상세 정보를 조회합니다. 주문 기본 정보, 주문 상품 목록의 스냅샷, 결제 상태, 포인트 사용/적립 요약을 함께 반환합니다.

- Method: `GET`
- Path: `/api/orders/{orderId}`
- 인증: 필요
- HTTP Status: `200 OK`

### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `orderId` | Long | 조회할 주문 ID |

### Response Data

```json
{
  "order": {
    "orderId": 200,
    "orderNumber": "ORD-20260529-000001",
    "status": "PAYMENT_PENDING",
    "totalAmount": 78000,
    "usedPointAmount": 5000,
    "orderedAt": "2026-05-29T18:30:00"
  },
  "items": [
    {
      "orderItemId": 400,
      "productId": 10,
      "productName": "무선 키보드",
      "quantity": 2,
      "refundedQuantity": 0,
      "status": "ORDERED",
      "unitPrice": 39000,
      "lineAmount": 78000
    }
  ],
  "payment": {
    "paymentId": 300,
    "portonePaymentId": "pay_4eb1c6ef-1d3b-4ee0-b48a-3ed567f9a0e7",
    "status": "PENDING",
    "type": "POINT_CARD",
    "totalAmount": 78000,
    "usedPointAmount": 5000,
    "pgAmount": 73000,
    "rewardPointAmount": 730,
    "approvedAt": null,
    "failedAt": null
  },
  "pointSummary": {
    "usedPointAmount": 5000,
    "rewardPointAmount": 730
  }
}
```

### 처리 규칙

- 토큰의 회원 ID를 기준으로 본인 주문만 조회할 수 있습니다.
- 주문 상품의 `productName`, `unitPrice`는 주문 생성 시점에 저장된 스냅샷 값입니다.
- `pointSummary.usedPointAmount`는 주문에 사용한 포인트 금액입니다.
- `pointSummary.rewardPointAmount`는 결제 완료 후 적립될 예정 포인트입니다.

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `ORDER_NOT_FOUND` | 404 | 주문 없음 |
| `ORDER_ACCESS_DENIED` | 403 | 타인의 주문 |
| `PAYMENT_NOT_FOUND` | 404 | 주문에 연결된 결제 없음 |

## 주문 상태 변경

회원이 결제대기 주문을 취소하거나, 결제대기 주문의 일부 주문상품을 취소합니다. 프로젝트 문서에서는 이 기능을 주문 상태 변경으로 부르며, 현재 구현된 실제 경로는 `/api/orders/{orderId}/status`입니다.

- Method: `PATCH`
- Path: `/api/orders/{orderId}/status`
- 인증: 필요
- HTTP Status: `200 OK`

### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `orderId` | Long | 주문 ID |

### Request Body

본문은 생략할 수 있습니다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `orderItemIds` | Long[] | N | 취소할 주문상품 ID 목록. `null` 또는 빈 배열이면 아직 취소되지 않은 모든 주문상품 취소 |

```json
{
  "orderItemIds": [400, 401]
}
```

### Response Data

```json
{
  "orderId": 200,
  "orderNumber": "ORD-20260529-000001",
  "previousOrderStatus": "PAYMENT_PENDING",
  "currentOrderStatus": "PARTIAL_CANCELED",
  "canceledAmount": 39000,
  "remainingTotalAmount": 39000,
  "restoredPointAmount": 2500,
  "remainingUsedPointAmount": 2500,
  "remainingPgAmount": 36500,
  "paymentStatus": "PENDING",
  "restoredStockItems": [
    {
      "orderItemId": 400,
      "productId": 10,
      "restoreQuantity": 1
    }
  ],
  "canceledAt": "2026-05-29T18:40:00"
}
```

전체 취소라면 `currentOrderStatus`는 `CANCELED`, `paymentStatus`는 `FAILED`, `remainingTotalAmount`는 `0`입니다.

### 처리 규칙

- 주문 소유자만 취소할 수 있습니다.
- 내부 결제 상태가 `PENDING`이고 주문 상태가 `PAYMENT_PENDING` 또는 `PARTIAL_CANCELED`인 경우만 직접 취소할 수 있습니다.
- `orderItemIds`가 없으면 아직 취소되지 않은 모든 주문상품을 취소합니다.
- 선택한 주문상품만 취소하면 주문 상태는 `PARTIAL_CANCELED`가 되고 결제 금액은 남은 주문 금액 기준으로 다시 계산됩니다.
- 전체 취소하면 주문 상태는 `CANCELED`, 결제 상태는 `FAILED`로 변경됩니다.
- 취소된 주문상품의 재고는 즉시 복구됩니다.
- 예약 차감된 포인트 중 취소 금액에 해당하는 금액은 `USE_CANCEL` 원장으로 복구됩니다.
- 주문 취소 API는 결제 전 `PENDING` 주문을 내부 상태 기준으로 정리하는 API입니다.
- 주문 도메인에서는 PortOne 결제 조회나 PG 취소를 호출하지 않습니다. 외부 결제가 완료된 주문은 결제 확정 또는 환불 흐름에서 처리합니다.

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `USER_NOT_FOUND` | 404 | 인증 사용자를 찾을 수 없음 |
| `ORDER_NOT_FOUND` | 404 | 주문 없음 |
| `ORDER_ACCESS_DENIED` | 403 | 타인의 주문 |
| `PAYMENT_NOT_FOUND` | 404 | 주문에 연결된 결제 없음 |
| `ORDER_ITEM_NOT_FOUND` | 404 | 주문상품 없음 또는 취소할 수 있는 주문상품 없음 |
| `INVALID_ORDER_STATUS` | 400 | 이미 취소된 주문상품을 다시 취소하려는 경우 |
| `ORDER_CANCEL_NOT_ALLOWED` | 409 | 직접 취소할 수 없는 주문/결제 상태 |
