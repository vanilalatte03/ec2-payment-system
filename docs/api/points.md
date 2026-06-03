# 포인트 API

포인트는 `users.point_balance` 잔액 스냅샷과 `point_transactions` 원장으로 관리합니다. 사용 예약, 사용 확정, 예약 취소, 적립, 사용복구, 적립회수는 모두 원장에 기록합니다.

환불로 기존 적립 포인트를 회수해야 할 때, 회수 대상 포인트가 회원의 현재 보유 포인트보다 크더라도 포인트 잔액은 음수로 만들지 않습니다.
실제 회수 가능한 포인트는 현재 보유 포인트 한도로 제한하고, 부족한 포인트 금액은 PG 환불 예정 금액에서 차감합니다.
또한 결제 시 포인트 사용 금액은 현재 보유 포인트와 주문 금액을 초과할 수 없으며, 최소 사용 단위는 1원입니다.

## 포인트 잔액 조회

내 현재 포인트 잔액을 조회합니다.

- Method: `GET`
- Path: `/api/points/balance`
- 인증: 필요
- HTTP Status: `200 OK`

### Response Data

```json
{
  "userId": 1,
  "balance": 5000
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `POINT_ACCOUNT_NOT_FOUND` | 404 | 포인트 계정 없음 |
| `POINT_LEDGER_SYNC_FAILED` | 500 | 원장 합계와 스냅샷 불일치 |

## 포인트 거래 내역 조회

내 포인트 거래 내역을 최신순으로 조회합니다.

- Method: `GET`
- Path: `/api/points/transactions`
- 인증: 필요
- HTTP Status: `200 OK`

### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `type` | PointTransactionType | N | 없음 | 거래 타입 |
| `page` | int | N | `0` | 페이지 번호 |
| `size` | int | N | `10` | 페이지 크기 |

### Response Data

```json
{
  "content": [
    {
      "pointTransactionId": 900,
      "paymentId": 300,
      "type": "EARN",
      "amount": 730,
      "createdAt": "2026-05-29T18:35:00+09:00"
    },
    {
      "pointTransactionId": 899,
      "paymentId": 300,
      "type": "USE",
      "amount": 5000,
      "createdAt": "2026-05-29T18:35:00+09:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 2,
  "totalPages": 1,
  "hasNext": false
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `INVALID_ENUM_VALUE` | 400 | 잘못된 포인트 거래 타입 |
| `INVALID_PAGINATION` | 400 | 페이지 번호 또는 크기 오류 |
| `POINT_ACCOUNT_NOT_FOUND` | 404 | 포인트 계정 없음 |
