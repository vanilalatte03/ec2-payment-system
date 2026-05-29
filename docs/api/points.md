# 포인트 API

포인트는 `user.point_snap` 잔액 스냅샷과 `point_transaction` 원장으로 관리합니다. 사용, 적립, 사용복구, 적립회수는 모두 원장에 기록합니다.

환불로 적립 포인트를 회수할 때 이미 사용한 포인트가 많으면 잔액이 음수가 될 수 있습니다. 이번 프로젝트에서는 즉시 적립 정책의 트레이드오프로 음수 잔액을 허용합니다. 단, 결제 시 포인트 사용은 현재 잔액을 초과할 수 없습니다.

## GET `/api/points/balance`

내 현재 포인트 잔액을 조회합니다.

- 인증: 필요
- HTTP Status: `200 OK`

### Response Data

```json
{
  "userId": 1,
  "balance": 5000,
  "negativeBalanceAllowed": true,
  "updatedAt": "2026-05-29T18:30:00+09:00"
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `POINT_ACCOUNT_NOT_FOUND` | 404 | 포인트 계정 없음 |
| `POINT_LEDGER_SYNC_FAILED` | 500 | 원장 합계와 스냅샷 불일치 |

## GET `/api/points/transactions`

내 포인트 거래 내역을 최신순으로 조회합니다.

- 인증: 필요
- HTTP Status: `200 OK`

### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `type` | PointTransactionType | N | 없음 | 거래 타입 |
| `page` | number | N | `0` | 페이지 번호 |
| `size` | number | N | `20` | 페이지 크기 |

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
  "size": 20,
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
