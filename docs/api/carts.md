# 장바구니 API

장바구니는 인증된 회원만 접근할 수 있고, 항상 토큰의 회원 기준으로 조회/수정합니다.

성공/실패 응답은 모두 [공통 응답 wrapper](./common.md#공통-응답)를 사용합니다.
아래 `Response Data` 예시는 wrapper의 `data` 안에 들어가는 값만 보여줍니다.

## 공통 정책

- 회원가입 시 회원별 장바구니가 함께 생성됩니다.
- 장바구니 조회는 상품이 없어도 성공하며, 빈 목록과 합계 `0`을 반환합니다.
- 상품 담기/수량 변경/상품 삭제/전체 비우기는 장바구니 버전 기반 낙관락을 사용합니다.
- 같은 장바구니를 동시에 수정하면 일부 요청은 `409 CONFLICT`를 받을 수 있습니다. 이 경우 클라이언트는 장바구니를 다시 조회한 뒤 사용자가 요청한 작업을 재시도할 수 있습니다.
- `cartTotalAmount`, `totalAmount`, `lineAmount`는 모두 원화 정수입니다.

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

새 장바구니 상품을 만들거나 기존 장바구니 상품 수량을 합산한 뒤, 변경된 장바구니 상품 정보와 장바구니 전체 합계 금액을 반환합니다.

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

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `cartItemId` | Long | 생성되었거나 수량이 합산된 장바구니 상품 ID |
| `productId` | Long | 상품 ID |
| `productName` | string | 상품명 |
| `quantity` | int | 장바구니에 최종 반영된 수량 |
| `unitPrice` | int | 현재 상품 단가 |
| `lineAmount` | Long | `unitPrice * quantity` |
| `cartTotalAmount` | Long | 현재 장바구니 전체 상품 합계 금액 |

### Response Example

```json
{
  "status": 200,
  "message": "요청이 성공했습니다.",
  "data": {
    "cartItemId": 100,
    "productId": 10,
    "productName": "무선 키보드",
    "quantity": 2,
    "unitPrice": 39000,
    "lineAmount": 78000,
    "cartTotalAmount": 78000
  }
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `VALIDATION_FAILED` | 400 | `productId` 또는 `quantity` 누락, 수량이 1 미만 |
| `CART_NOT_FOUND` | 404 | 회원의 장바구니가 없음 |
| `PRODUCT_NOT_FOUND` | 404 | 상품이 없음 |
| `PRODUCT_NOT_ON_SALE` | 400 | 판매중 상품이 아님 |
| `CART_STOCK_EXCEEDED` | 409 | 합산 수량이 현재 재고 초과 |
| `CONFLICT` | 409 | 같은 장바구니를 동시에 수정해서 버전 충돌 발생 |

## 장바구니 조회

내 장바구니 상품 목록과 합계 금액을 조회합니다.

- Method: `GET`
- Path: `/api/carts`
- 인증: 필요
- HTTP Status: `200 OK`

### Response Data

`items`에는 장바구니 상품별 현재 상품 정보가 함께 포함됩니다.
상품 상태나 재고가 바뀐 경우에도 조회 시점의 `stock`, `status`가 내려갑니다.

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

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `cartId` | Long 또는 null | 장바구니 ID. 장바구니가 없으면 `null` |
| `items` | array | 장바구니 상품 목록 |
| `items[].cartItemId` | Long | 장바구니 상품 ID |
| `items[].productId` | Long | 상품 ID |
| `items[].productName` | string | 상품명 |
| `items[].quantity` | int | 장바구니에 담긴 수량 |
| `items[].unitPrice` | int | 현재 상품 단가 |
| `items[].lineAmount` | Long | `unitPrice * quantity` |
| `items[].stock` | int | 현재 상품 재고 |
| `items[].status` | ProductStatus | 현재 상품 상태. `ON_SALE`, `SOLD_OUT`, `DISCONTINUED` |
| `totalQuantity` | int | 장바구니 전체 상품 수량 합계 |
| `totalAmount` | Long | 장바구니 전체 상품 금액 합계 |

### Empty Cart Response Data

회원의 장바구니에 담긴 상품이 없으면 빈 목록과 합계 `0`을 반환합니다.

```json
{
  "cartId": 1,
  "items": [],
  "totalQuantity": 0,
  "totalAmount": 0
}
```

장바구니 자체가 아직 없으면 `cartId`만 `null`이고 나머지는 동일하게 빈 값으로 반환합니다.

```json
{
  "cartId": null,
  "items": [],
  "totalQuantity": 0,
  "totalAmount": 0
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |

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

변경된 장바구니 상품 정보와 변경 이후 장바구니 전체 합계 금액을 반환합니다.

```json
{
  "cartItemId": 100,
  "productId": 10,
  "productName": "무선 키보드",
  "quantity": 3,
  "unitPrice": 39000,
  "lineAmount": 117000,
  "cartTotalAmount": 117000
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `cartItemId` | Long | 변경된 장바구니 상품 ID |
| `productId` | Long | 상품 ID |
| `productName` | string | 상품명 |
| `quantity` | int | 변경 후 수량 |
| `unitPrice` | int | 현재 상품 단가 |
| `lineAmount` | Long | `unitPrice * quantity` |
| `cartTotalAmount` | Long | 변경 이후 장바구니 전체 상품 합계 금액 |

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `VALIDATION_FAILED` | 400 | `quantity` 누락 또는 수량이 1 미만 |
| `CART_ITEM_NOT_FOUND` | 404 | 장바구니 상품이 없음 |
| `CART_ITEM_ACCESS_DENIED` | 403 | 타인의 장바구니 상품 |
| `CART_NOT_FOUND` | 404 | 장바구니 상품이 가리키는 장바구니가 없음 |
| `PRODUCT_NOT_ON_SALE` | 400 | 판매중 상품이 아님 |
| `CART_STOCK_EXCEEDED` | 409 | 변경 수량이 현재 재고 초과 |
| `CONFLICT` | 409 | 같은 장바구니를 동시에 수정해서 버전 충돌 발생 |

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

삭제 성공 여부, 삭제한 장바구니 상품 ID, 삭제 이후 장바구니 전체 합계 금액을 반환합니다.

```json
{
  "deleted": true,
  "cartItemId": 100,
  "cartTotalAmount": 0
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `deleted` | boolean | 삭제 성공 여부. 성공 시 `true` |
| `cartItemId` | Long | 삭제한 장바구니 상품 ID |
| `cartTotalAmount` | Long | 삭제 이후 장바구니 전체 상품 합계 금액 |

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `CART_ITEM_NOT_FOUND` | 404 | 장바구니 상품이 없음 |
| `CART_ITEM_ACCESS_DENIED` | 403 | 타인의 장바구니 상품 |
| `CART_NOT_FOUND` | 404 | 장바구니 상품이 가리키는 장바구니가 없음 |
| `CONFLICT` | 409 | 같은 장바구니를 동시에 수정해서 버전 충돌 발생 |

## 장바구니 전체 비우기

내 장바구니를 전체 비웁니다.

- Method: `DELETE`
- Path: `/api/carts`
- 인증: 필요
- HTTP Status: `200 OK`

### Response Data

삭제한 장바구니 상품 개수를 반환합니다.
이미 비어 있는 장바구니를 비우면 `deletedCount`는 `0`입니다.

```json
{
  "deletedCount": 3
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `deletedCount` | int | 삭제된 장바구니 상품 개수 |

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `CART_NOT_FOUND` | 404 | 회원의 장바구니가 없음 |
| `CONFLICT` | 409 | 같은 장바구니를 동시에 수정해서 버전 충돌 발생 |
