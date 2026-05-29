# 웹훅 API

PortOne 웹훅은 JWT 인증을 사용하지 않습니다. PortOne 웹훅 시크릿으로 서명을 검증하고, 본문 데이터는 최종 신뢰하지 않습니다. 본문에서 `paymentId`만 추출한 뒤 PortOne 결제 단건 조회 API로 최신 결제 정보를 직접 조회합니다.

PortOne V2 웹훅은 Standard Webhooks 방식의 메시지 검증을 지원합니다. 구현 시 raw body와 요청 헤더를 그대로 사용해야 합니다.

참고:

- [PortOne 웹훅 연동하기](https://developers.portone.io/opi/ko/integration/webhook/readme-v2?v=v2)
- [Standard Webhooks specification](https://github.com/standard-webhooks/standard-webhooks/blob/main/spec/standard-webhooks.md)

## POST `/api/webhooks/portone`

PortOne 결제 결과 웹훅을 수신합니다.

- 인증: PortOne 웹훅 서명 검증
- HTTP Status: `200 OK`
- Content-Type: `application/json`

### Headers

Standard Webhooks 기준 헤더입니다. PortOne 서버 SDK를 사용하면 직접 헤더명을 파싱하지 않고 검증할 수 있습니다.

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `webhook-id` | Y | 웹훅 메시지 ID |
| `webhook-timestamp` | Y | 전송 시각 Unix timestamp |
| `webhook-signature` | Y | HMAC-SHA256 서명 |

### Request Body

PortOne `2024-04-25` 웹훅 버전 예시입니다.

```json
{
  "type": "Transaction.Paid",
  "timestamp": "2026-05-29T09:35:00.000Z",
  "data": {
    "paymentId": "pay_20260529_000001",
    "storeId": "store-xxxxxxxx",
    "transactionId": "transaction-xxxxxxxx"
  }
}
```

### Response Data

```json
{
  "received": true,
  "processed": true,
  "portonePaymentId": "pay_20260529_000001",
  "paymentStatus": "COMPLETED"
}
```

이미 처리된 결제이거나 처리 대상이 아닌 이벤트면 `200 OK`를 반환합니다.

```json
{
  "received": true,
  "processed": false,
  "reason": "ALREADY_PROCESSED"
}
```

### 처리 규칙

- 서명 검증 실패 시 400을 반환합니다.
- 알 수 없는 `type`은 에러로 처리하지 않고 무시한 뒤 200을 반환합니다.
- `Transaction.Paid`는 결제 확정 API와 동일한 결제 확정 도메인 서비스를 호출합니다.
- `Transaction.Failed`는 결제대기 주문을 주문취소/결제실패로 정리하고 선차감 재고를 복구합니다.
- `Transaction.Cancelled`, `Transaction.PartialCancelled`는 서버가 요청한 환불/취소의 결과 동기화 용도로만 사용합니다.
- 본문 금액이나 상태는 최종 신뢰하지 않고 PortOne API 조회 결과만 사용합니다.
- PortOne 웹훅은 재전송될 수 있으므로 항상 멱등하게 처리합니다.

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `WEBHOOK_SIGNATURE_INVALID` | 400 | 서명 검증 실패 |
| `WEBHOOK_PAYLOAD_INVALID` | 400 | JSON 파싱 실패 |
| `WEBHOOK_PAYMENT_ID_MISSING` | 400 | 결제 이벤트인데 `paymentId` 없음 |
| `PAYMENT_NOT_FOUND` | 404 | 내부 결제 레코드 없음 |
| `PAYMENT_IN_PROGRESS` | 409 | 같은 결제 건 처리 중 |
| `EXTERNAL_API_FAILED` | 502 | PortOne 결제 조회 실패 |
