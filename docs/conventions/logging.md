# Logging Convention

로그에는 민감 정보를 남기지 않는다.

로그 금지 대상:

- JWT
- password
- billingKey
- secret key
- access token
- refresh token
- 카드 전체 번호
- 개인정보 전체 값

허용 가능한 로그:

```
portonePaymentId
orderId
userId
status
errorCode
```

단, 운영 환경에서는 개인정보성 값도 필요 최소한으로 남긴다.
