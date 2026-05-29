# 주문 API

주문은 결제와 환불의 기준 단위입니다. 주문 생성 후 상품/수량/금액 수정은 불가하며, 상태 변경은 결제 확정, 결제 실패 정리, 주문 취소, 환불 흐름에서만 발생합니다.

## 주문서 미리보기

장바구니 상품을 결제 직전 주문서 형태로 미리보기합니다. 스냅샷 저장 전이므로 상품의 현재가와 현재 재고를 기준으로 응답합니다.

- Method: `GET`
- Path: `/api/orders/preview`
- 인증: 필요
- HTTP Status: `200 OK`

### Query Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `cartItemIds` | number[] | N | 선택 장바구니 상품 ID 목록. 비어 있으면 장바구니 전체 |

예: `/api/orders/preview?cartItemIds=100&cartItemIds=101`

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
      "stock": 12,
      "status": "ON_SALE"
    }
  ],
  "totalAmount": 78000,
  "availablePoint": 5000,
  "maxUsablePoint": 5000
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `CART_EMPTY` | 400 | 미리보기 대상 장바구니 상품 없음 |
| `CART_ITEM_NOT_FOUND` | 404 | 선택한 장바구니 상품 없음 |
| `CART_ITEM_ACCESS_DENIED` | 403 | 타인의 장바구니 상품 |
| `PRODUCT_NOT_ON_SALE` | 400 | 판매중 상품이 아님 |
| `CART_STOCK_EXCEEDED` | 409 | 장바구니 수량이 현재 재고 초과 |

## 주문/결제 생성

주문서에서 결제하기를 누른 시점에 주문과 결제를 동시에 생성합니다. 단일 트랜잭션 안에서 재고를 검증하고 선차감합니다. 하나라도 재고가 부족하면 전체 주문 생성을 롤백합니다.

- Method: `POST`
- Path: `/api/orders`
- 인증: 필요
- HTTP Status: `201 Created`

### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `cartItemIds` | number[] | N | 주문할 장바구니 상품 ID 목록. 비어 있으면 장바구니 전체 |
| `usePointAmount` | number | Y | 사용할 포인트 금액. 0 이상 |

```json
{
  "cartItemIds": [100, 101],
  "usePointAmount": 5000
}
```

### Response Data

```json
{
  "orderId": 200,
  "orderNumber": "ORD-20260529-000001",
  "orderStatus": "PAYMENT_PENDING",
  "paymentId": 300,
  "portonePaymentId": "pay_20260529_000001",
  "paymentStatus": "PENDING",
  "paymentType": "POINT_CARD",
  "totalAmount": 78000,
  "usePointAmount": 5000,
  "pgAmount": 73000,
  "nextAction": "OPEN_PORTONE_PAYMENT",
  "items": [
    {
      "orderItemId": 400,
      "productId": 10,
      "productName": "무선 키보드",
      "quantity": 2,
      "unitPrice": 39000,
      "lineAmount": 78000
    }
  ]
}
```

`pgAmount`가 `0`이면 `paymentType`은 `POINT_ONLY`, `nextAction`은 `CONFIRM_POINT_ONLY`로 내려줍니다. 이 경우 클라이언트는 PortOne 결제창을 열지 않고 결제 확정 API를 호출합니다.

### 처리 규칙

- 주문 상품에는 주문 생성 시점의 상품명과 가격 스냅샷을 저장합니다.
- 결제 레코드는 `PENDING` 상태로 함께 생성합니다.
- 서버가 PortOne에 전달할 `portonePaymentId`를 미리 채번하고 결제 레코드에 저장합니다.
- 장바구니는 주문 생성 시 비우지 않습니다. 결제 완료 시점에 비웁니다.
- 포인트 잔액이 부족하면 주문과 결제는 생성하지 않습니다.

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `VALIDATION_FAILED` | 400 | 사용할 포인트가 음수 |
| `CART_EMPTY` | 400 | 주문 대상 장바구니 상품 없음 |
| `CART_ITEM_NOT_FOUND` | 404 | 선택한 장바구니 상품 없음 |
| `CART_ITEM_ACCESS_DENIED` | 403 | 타인의 장바구니 상품 |
| `PRODUCT_NOT_ON_SALE` | 400 | 판매중 상품이 아님 |
| `ORDER_STOCK_SHORTAGE` | 409 | 재고 검증/차감 중 재고 부족 |
| `INSUFFICIENT_POINT` | 400 | 포인트 잔액 부족 |

## 주문 내역 조회

내 주문 목록을 최신순으로 조회합니다.

- Method: `GET`
- Path: `/api/orders`
- 인증: 필요
- HTTP Status: `200 OK`

### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `status` | OrderStatus | N | 없음 | 주문 상태 |
| `page` | number | N | `0` | 페이지 번호 |
| `size` | number | N | `20` | 페이지 크기 |

### Response Data

```json
{
  "content": [
    {
      "orderId": 200,
      "orderNumber": "ORD-20260529-000001",
      "status": "COMPLETED",
      "totalAmount": 78000,
      "usedPointAmount": 5000,
      "pgAmount": 73000,
      "orderedAt": "2026-05-29T18:30:00+09:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `INVALID_ENUM_VALUE` | 400 | 잘못된 주문 상태 |
| `INVALID_PAGINATION` | 400 | 페이지 번호 또는 크기 오류 |

## 주문 상세 조회

주문 상세, 주문 상품, 결제 상태, 포인트 사용/적립 요약을 조회합니다.

- Method: `GET`
- Path: `/api/orders/{orderId}`
- 인증: 필요
- HTTP Status: `200 OK`

### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `orderId` | number | 주문 ID |

### Response Data

```json
{
  "orderId": 200,
  "orderNumber": "ORD-20260529-000001",
  "status": "COMPLETED",
  "totalAmount": 78000,
  "createdAt": "2026-05-29T18:30:00+09:00",
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
  ],
  "payment": {
    "paymentId": 300,
    "portonePaymentId": "pay_20260529_000001",
    "status": "COMPLETED",
    "type": "POINT_CARD",
    "totalAmount": 78000,
    "usedPointAmount": 5000,
    "pgAmount": 73000,
    "rewardPointAmount": 730,
    "approvedAt": "2026-05-29T18:35:00+09:00"
  },
  "pointSummary": {
    "usedPointAmount": 5000,
    "rewardPointAmount": 730,
    "restoredPointAmount": 0,
    "canceledEarnPointAmount": 0
  }
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `ORDER_NOT_FOUND` | 404 | 주문 없음 |
| `ORDER_ACCESS_DENIED` | 403 | 타인의 주문 |

## 주문 상태 변경

주문 상태를 변경합니다. 회원이 직접 호출하는 상태 변경은 결제대기 주문을 `CANCELED`로 변경하는 경우만 허용합니다. `COMPLETED` 전이는 결제 확정 로직에서 처리합니다.

- Method: `PATCH`
- Path: `/api/orders/{orderId}/status`
- 인증: 필요
- HTTP Status: `200 OK`

### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `orderId` | number | 주문 ID |

### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `status` | OrderStatus | Y | 변경할 주문 상태. 직접 요청은 `CANCELED`만 허용 |

```json
{
  "status": "CANCELED"
}
```

### Response Data

```json
{
  "orderId": 200,
  "orderNumber": "ORD-20260529-000001",
  "previousOrderStatus": "PAYMENT_PENDING",
  "currentOrderStatus": "CANCELED",
  "paymentStatus": "FAILED",
  "restoredStockItems": [
    {
      "productId": 10,
      "restoredQuantity": 2
    }
  ],
  "canceledAt": "2026-05-29T18:40:00+09:00"
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `VALIDATION_FAILED` | 400 | 상태 값 누락 |
| `INVALID_ENUM_VALUE` | 400 | 잘못된 주문 상태 |
| `ORDER_NOT_FOUND` | 404 | 주문 없음 |
| `ORDER_ACCESS_DENIED` | 403 | 타인의 주문 |
| `ORDER_CANCEL_NOT_ALLOWED` | 409 | 결제대기 상태가 아니거나 직접 변경할 수 없는 상태 |
