# 포인트 API

포인트는 `users.point_balance` 잔액 스냅샷과 `point_transactions` 원장으로 관리합니다. 주문 생성 시 포인트를 먼저 예약 차감하고 `USE_RESERVE` 원장을 남깁니다. 결제 확정 시에는 이 예약 원장을 새로 만들지 않고 `USE`로 변경합니다.
예약 취소, 적립, 사용 포인트 복구, 적립 포인트 회수는 각각 별도 원장으로 기록합니다. 각 원장은 `idempotencyKey`로 중복 생성을 방지합니다. 결제 과정의 원장은 `PAYMENT:{paymentId}:{type}`, 환불 과정의 원장은 `REFUND:{refundId}:{type}` 형식을 사용합니다.

환불로 기존 적립 포인트를 회수해야 할 때, 회수 대상 포인트가 회원의 현재 보유 포인트보다 크더라도 포인트 잔액은 음수로 만들지 않습니다.
실제 회수 가능한 포인트는 현재 보유 포인트 한도로 제한하고, 부족한 포인트 금액은 PG 환불 예정 금액에서 차감합니다.
적립 포인트 회수(`EARN_CANCEL`)는 실제 회수 가능 포인트가 0원이어도 같은 환불 재시도를 막기 위해 0원 원장을 남길 수 있습니다.
또한 결제 시 포인트 사용 금액은 현재 보유 포인트와 주문 금액을 초과할 수 없으며, 최소 사용 단위는 1원입니다.

## 포인트 원장 타입

| 타입 | 발생 시점 | 잔액 영향 | 멱등 키 기준 |
| --- | --- | --- | --- |
| `USE_RESERVE` | 주문 생성 시 사용 포인트 예약 | 감소 | `PAYMENT:{paymentId}:USE_RESERVE` |
| `USE` | 결제 확정 시 예약 원장을 최종 사용으로 변경 | 추가 변화 없음 | `PAYMENT:{paymentId}:USE` |
| `USE_CANCEL` | 결제 실패 또는 주문 취소 시 예약 포인트 복구 | 증가 | `PAYMENT:{paymentId}:USE_CANCEL` |
| `EARN` | 결제 완료 후 PG 실결제 금액의 1% 적립 | 증가 | `PAYMENT:{paymentId}:EARN` |
| `USE_RESTORE` | 환불 시 결제에 사용한 포인트 복구 | 증가 | `REFUND:{refundId}:USE_RESTORE` |
| `EARN_CANCEL` | 환불 시 결제 완료 때 적립한 포인트 회수 | 감소 | `REFUND:{refundId}:EARN_CANCEL` |

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
