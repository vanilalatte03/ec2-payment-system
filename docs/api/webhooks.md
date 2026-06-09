# 웹훅 API

PortOne 웹훅은 JWT 인증을 사용하지 않습니다. PortOne 웹훅 시크릿으로 서명을 검증하고, 본문 데이터는 최종 신뢰하지 않습니다. 본문에서 `paymentId`만 추출한 뒤 PortOne 결제 단건 조회 API로 최신 결제 정보를 직접 조회합니다.

PortOne V2 웹훅은 Standard Webhooks 방식의 메시지 검증을 지원합니다. 구현 시 raw body와 요청 헤더를 그대로 사용해야 합니다.

성공/실패 응답은 모두 [공통 응답 wrapper](./common.md#공통-응답)를 사용합니다.

아래 `Response Data` 예시는 wrapper의 `data` 안에 들어가는 값만 보여줍니다.

HTTP 상태 코드는 엔드포인트별 값을 따르지만, 응답 body의 `status`는 공통 wrapper 규칙에 따라 `200`으로 고정됩니다.

참고:

- [PortOne 웹훅 연동하기](https://developers.portone.io/opi/ko/integration/webhook/readme-v2?v=v2)
- [Standard Webhooks specification](https://github.com/standard-webhooks/standard-webhooks/blob/main/spec/standard-webhooks.md)

## PortOne 웹훅 수신

PortOne 결제 결과 웹훅을 수신합니다.

- Method: `POST`
- Path: `/api/webhooks/portone`
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

#### PortoneWebhookReceiveResponse

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `received` | `Boolean` | 웹훅을 서버가 정상 수신했는지 여부 |
| `processed` | `Boolean` | 처리 대상 웹훅의 결제 확정 처리까지 완료했는지 여부 |
| `portonePaymentId` | `String` | 처리 대상 PortOne 결제 ID. 중복 수신 또는 무시 이벤트이면 `null` |
| `reason` | `String` | 처리 결과 사유. `PROCESSED`, `DUPLICATE_WEBHOOK_ID`, `UNSUPPORTED_EVENT_TYPE` 등 |

```json
{
  "received": true,
  "processed": true,
  "portonePaymentId": "pay_20260529_000001",
  "reason": "PROCESSED"
}
```

이미 처리 완료됐거나 무시 처리된 같은 `webhook-id`가 다시 들어오면 중복 수신으로 보고 `200 OK`를 반환합니다.
단, 같은 `webhook-id`의 `Transaction.Paid` 이벤트가 `FAILED` 또는 `RECEIVED` 상태로 남아 있으면 중복 응답을 반환하지 않고 결제 확정을 다시 시도합니다.

```json
{
  "received": true,
  "processed": false,
  "portonePaymentId": null,
  "reason": "DUPLICATE_WEBHOOK_ID"
}
```

현재 처리 대상이 아닌 이벤트는 `IGNORE` 상태로 저장하고 `200 OK`를 반환합니다.

```json
{
  "received": true,
  "processed": false,
  "portonePaymentId": null,
  "reason": "UNSUPPORTED_EVENT_TYPE"
}
```

### 처리 규칙

- 서명 검증 실패 시 400을 반환합니다.
- `Transaction.Paid`는 결제 확정 API와 동일한 결제 확정 도메인 서비스를 호출합니다.
- `Transaction.Cancelled`, `Transaction.PartialCancelled`는 환불 완료 웹훅으로 보고 `cancellationId` 기준으로 환불 완료 처리를 수행합니다.
- `Transaction.Failed`, `Transaction.CancelPending` 등 현재 처리 대상이 아닌 이벤트는 `IGNORE` 상태로 저장한 뒤 200을 반환합니다.
- 같은 `webhook-id`가 이미 `PROCESSED` 또는 `IGNORE` 상태면 중복 수신으로 보고 추가 처리를 하지 않습니다.
- 같은 `webhook-id`의 처리 대상 이벤트가 `FAILED` 또는 `RECEIVED` 상태로 남아 있으면 재전송 시 처리를 다시 시도합니다.
- 본문 금액이나 상태는 최종 신뢰하지 않고 PortOne API 조회 결과만 사용합니다.
- PortOne 웹훅은 재전송될 수 있으므로 항상 멱등하게 처리합니다.

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `WEBHOOK_SIGNATURE_INVALID` | 400 | 필수 웹훅 헤더 누락 또는 서명 검증 실패 |
| `WEBHOOK_PAYLOAD_INVALID` | 400 | 본문 누락, 빈 본문 또는 JSON 파싱 실패 |
| `WEBHOOK_PAYMENT_ID_MISSING` | 400 | 결제 이벤트인데 `paymentId` 없음 |
| `WEBHOOK_CANCELLATION_ID_MISSING` | 400 | 환불 완료 이벤트인데 `cancellationId` 없음 |
| `PAYMENT_NOT_FOUND` | 404 | 내부 결제 레코드 없음 |
| `PAYMENT_PORTONE_ID_MISMATCH` | 400 | PortOne 결제 조회 결과의 결제 식별자가 내부 결제 식별자와 다름 |
| `PAYMENT_STATUS_NOT_PAID` | 400 | PortOne 결제 상태가 성공 상태가 아님 |
| `PAYMENT_AMOUNT_MISMATCH` | 400 | PortOne 승인 금액과 서버 산정 PG 금액 불일치 |
| `INVALID_ORDER_STATUS` | 400 | 내부 주문이 결제 확정 가능한 상태가 아님 |
| `CONFLICT` | 409 | 내부 결제가 확정 가능한 상태가 아님 |
| `EXTERNAL_API_FAILED` | 502 | PortOne 결제 조회 실패 |
| `PAYMENT_COMPENSATION_FAILED` | 502 | 외부 결제 성공 후 내부 확정 실패에 대한 보상 취소 실패 |
