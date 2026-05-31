# API Response Convention

응답 형식은 프로젝트 전체에서 통일한다.

성공 응답 예시:

```json
{
  "success": true,
  "data": {},
  "message": "요청이 성공했습니다."
}
```

실패 응답 예시:

```json
{
  "success": false,
  "code": "PAYMENT_AMOUNT_MISMATCH",
  "message": "결제 금액이 주문 금액과 일치하지 않습니다."
}
```

Controller마다 응답 형식이 달라지지 않도록 한다.
