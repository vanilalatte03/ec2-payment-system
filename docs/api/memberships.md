# 멤버십 API

도전 기능 범위입니다. 멤버십 등급은 누적 결제 금액에 따라 자동 갱신되고, 결제 시 포인트 적립률에 사용됩니다.

포인트 적립률은 이번 결제 직전까지의 누적 결제 금액으로 판단합니다. 현재 결제 금액은 결제 완료 후 누적 금액에 반영되어 다음 결제부터 등급 혜택에 영향을 줍니다.

## GET `/api/memberships/me`

내 누적 결제 금액, 현재 등급, 다음 등급까지 남은 금액을 조회합니다.

- 인증: 필요
- HTTP Status: `200 OK`

### Response Data

```json
{
  "userId": 1,
  "grade": "VIP",
  "rewardRate": 0.05,
  "accumulatedPaymentAmount": 75000,
  "nextGrade": "VVIP",
  "amountToNextGrade": 25000,
  "updatedAt": "2026-05-29T18:35:00+09:00"
}
```

`VVIP`는 다음 등급이 없으므로 `nextGrade`는 `null`, `amountToNextGrade`는 `0`입니다.

### 등급 갱신 규칙

- 신규 가입 시 기본 등급은 `NORMAL`입니다.
- 결제 완료 트랜잭션 안에서 PG 실결제 금액을 누적 결제 금액에 더하고 등급을 재계산합니다.
- 환불 트랜잭션 안에서 환불 금액 중 실제 매출 차감 금액을 누적 결제 금액에서 빼고 등급을 재계산합니다.
- 누적 결제 금액은 음수가 될 수 없으며 최소 `0`입니다.

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `MEMBERSHIP_NOT_FOUND` | 404 | 회원의 멤버십 정보 없음 |
