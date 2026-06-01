# 상품 API

상품은 조회 중심 도메인입니다. 생성, 수정, 삭제는 관리자 기능으로 보고 이번 범위에서 제외합니다.

## 상품 목록 조회

상품 목록을 필터링, 정렬, 페이지네이션하여 조회합니다.

- Method: `GET`
- Path: `/api/products`
- 인증: 불필요
- HTTP Status: `200 OK`

### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `category` | string | N | 없음       | 카테고리 |
| `minPrice` | int | N | 없음       | 최소 판매가 |
| `maxPrice` | int | N | 없음       | 최대 판매가 |
| `status` | ProductStatus | N | 없음       | `ON_SALE`, `SOLD_OUT`, `DISCONTINUED` |
| `sort` | string | N | `LATEST` | `LATEST`, `PRICE_ASC`, `PRICE_DESC` |
| `page` | int | N | `0`      | 페이지 번호 |
| `size` | int | N | `10`     | 페이지 크기 |

### Response Data

```json
{
  "content": [
    {
      "productId": 10,
      "name": "무선 키보드",
      "price": 39000,
      "stock": 12,
      "category": "ELECTRONICS",
      "status": "ON_SALE",
      "createdAt": "2026-05-29T18:30:00+09:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 가격 범위가 음수이거나 `minPrice > maxPrice` |
| `INVALID_ENUM_VALUE` | 400 | 잘못된 `status` 또는 `sort` |
| `INVALID_PAGINATION` | 400 | 페이지 번호 또는 크기 오류 |

## 상품 단건 조회

상품 상세 정보를 조회합니다.

- Method: `GET`
- Path: `/api/products/{productId}`
- 인증: 불필요
- HTTP Status: `200 OK`

### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `productId` | Long | 상품 ID |

### Response Data

```json
{
  "productId": 10,
  "name": "무선 키보드",
  "price": 39000,
  "stock": 12,
  "category": "ELECTRONICS",
  "status": "ON_SALE",
  "description": "저소음 무선 키보드입니다.",
  "createdAt": "2026-05-29T18:30:00+09:00",
  "updatedAt": "2026-05-29T18:30:00+09:00"
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `PRODUCT_NOT_FOUND` | 404 | 상품이 없음 |
