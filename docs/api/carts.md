# 장바구니 API

장바구니는 인증된 회원만 접근할 수 있고, 항상 토큰의 회원 기준으로 조회/수정합니다.

## 상품 담기

상품을 장바구니에 담습니다. 같은 상품이 이미 있으면 수량을 합산합니다.

- Method: `POST`
- Path: `/api/carts/items`
- 인증: 필요
- HTTP Status: `201 Created`

### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `productId` | Long | Y | 상품 ID |
| `quantity` | int | Y | 담을 수량. 1 이상 |

```json
{
  "productId": 10,
  "quantity": 2
}
```

### Response Data

```json
{
  "cartItemId": 100,
  "productId": 10,
  "productName": "무선 키보드",
  "quantity": 2,
  "unitPrice": 39000,
  "lineAmount": 78000,
  "cartTotalAmount": 78000
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `VALIDATION_FAILED` | 400 | 수량이 1 미만 |
| `PRODUCT_NOT_FOUND` | 404 | 상품이 없음 |
| `PRODUCT_NOT_ON_SALE` | 400 | 판매중 상품이 아님 |
| `CART_STOCK_EXCEEDED` | 409 | 합산 수량이 현재 재고 초과 |

## 장바구니 조회

내 장바구니 상품 목록과 합계 금액을 조회합니다.

- Method: `GET`
- Path: `/api/carts`
- 인증: 필요
- HTTP Status: `200 OK`

### Response Data

```json
{
  "cartId": 1,
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
  "totalQuantity": 2,
  "totalAmount": 78000
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `CART_NOT_FOUND` | 404 | 회원의 장바구니가 없음 |

## 장바구니 수량 변경

장바구니 상품 수량을 변경합니다.

- Method: `PATCH`
- Path: `/api/carts/items/{cartItemId}`
- 인증: 필요
- HTTP Status: `200 OK`

### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `cartItemId` | Long | 장바구니 상품 ID |

### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `quantity` | int | Y | 변경할 수량. 1 이상 |

```json
{
  "quantity": 3
}
```

### Response Data

```json
{
  "cartItemId": 100,
  "productId": 10,
  "quantity": 3,
  "unitPrice": 39000,
  "lineAmount": 117000,
  "cartTotalAmount": 117000
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `VALIDATION_FAILED` | 400 | 수량이 1 미만 |
| `CART_ITEM_NOT_FOUND` | 404 | 장바구니 상품이 없음 |
| `CART_ITEM_ACCESS_DENIED` | 403 | 타인의 장바구니 상품 |
| `PRODUCT_NOT_ON_SALE` | 400 | 판매중 상품이 아님 |
| `CART_STOCK_EXCEEDED` | 409 | 변경 수량이 현재 재고 초과 |

## 장바구니 상품 개별 삭제

장바구니 상품 1건을 삭제합니다.

- Method: `DELETE`
- Path: `/api/carts/items/{cartItemId}`
- 인증: 필요
- HTTP Status: `200 OK`

### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `cartItemId` | Long | 장바구니 상품 ID |

### Response Data

```json
{
  "deleted": true,
  "cartItemId": 100,
  "cartTotalAmount": 0
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `CART_ITEM_NOT_FOUND` | 404 | 장바구니 상품이 없음 |
| `CART_ITEM_ACCESS_DENIED` | 403 | 타인의 장바구니 상품 |

## 장바구니 전체 비우기

내 장바구니를 전체 비웁니다.

- Method: `DELETE`
- Path: `/api/carts`
- 인증: 필요
- HTTP Status: `200 OK`

### Response Data

```json
{
  "deletedCount": 3
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `CART_NOT_FOUND` | 404 | 회원의 장바구니가 없음 |
