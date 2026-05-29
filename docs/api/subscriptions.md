# 구독 API

도전 기능 범위입니다. 구독 결제는 PortOne 빌링키 기반으로 처리하고, 서버 스케줄러가 매일 00:00 KST에 정기 결제 대상 구독을 조회해 결제를 요청합니다.

필수 카드 결제는 KG이니시스 인증결제 채널을 사용하고, 구독 결제는 PortOne 콘솔에 별도로 추가한 토스페이먼츠 빌링 채널을 사용합니다.

## POST `/api/billing-methods`

PortOne에서 발급받은 빌링키와 카드 정보를 저장합니다. 빌링키 발급창은 클라이언트가 PortOne SDK로 진행하고, 서버는 발급 결과를 저장합니다.

- 인증: 필요
- HTTP Status: `201 Created`

### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `billingKey` | string | Y | PortOne 빌링키 |
| `cardCompany` | string | Y | 카드사명 |
| `cardMaskedNumber` | string | Y | 마스킹 카드 번호 |

```json
{
  "billingKey": "billing-key-xxxxxxxx",
  "cardCompany": "TOSS",
  "cardMaskedNumber": "1234-****-****-5678"
}
```

### Response Data

```json
{
  "billingMethodId": 10,
  "cardCompany": "TOSS",
  "cardMaskedNumber": "1234-****-****-5678",
  "status": "ACTIVE",
  "createdAt": "2026-05-29T18:30:00+09:00"
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `VALIDATION_FAILED` | 400 | 빌링키 또는 카드 정보 누락 |
| `EXTERNAL_API_FAILED` | 502 | PortOne 빌링키 검증 실패 |

## GET `/api/billing-methods`

내 결제 수단 목록을 조회합니다.

- 인증: 필요
- HTTP Status: `200 OK`

### Response Data

```json
{
  "billingMethods": [
    {
      "billingMethodId": 10,
      "cardCompany": "TOSS",
      "cardMaskedNumber": "1234-****-****-5678",
      "status": "ACTIVE",
      "createdAt": "2026-05-29T18:30:00+09:00"
    }
  ]
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |

## POST `/api/subscriptions`

요금제와 결제 수단을 선택해 구독을 시작합니다. 첫 결제를 즉시 수행하고 성공하면 다음 결제일을 설정합니다.

- 인증: 필요
- HTTP Status: `201 Created`

### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `planId` | number | Y | 구독 요금제 ID |
| `billingMethodId` | number | Y | 결제 수단 ID |

```json
{
  "planId": 1,
  "billingMethodId": 10
}
```

### Response Data

```json
{
  "subscriptionId": 100,
  "planId": 1,
  "planName": "BASIC",
  "status": "ACTIVE",
  "monthlyAmount": 9900,
  "billingMethodId": 10,
  "firstInvoice": {
    "subscriptionInvoiceId": 1000,
    "billingPeriod": "2026-05",
    "status": "COMPLETED",
    "paidAmount": 9900,
    "paidAt": "2026-05-29T18:35:00+09:00"
  },
  "startedAt": "2026-05-29T18:35:00+09:00",
  "nextPaymentDate": "2026-06-29"
}
```

### 처리 규칙

- 첫 결제 실패 시 구독은 성립하지 않은 것으로 보고 `CANCELED` 또는 생성 롤백으로 정리합니다.
- 같은 회원에게 활성 구독은 1개만 허용하는 것을 권장합니다.
- 다음 결제일은 시작일 + 1개월입니다. 말일 가입자는 다음 달 말일로 보정합니다.

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `SUBSCRIPTION_PLAN_NOT_FOUND` | 404 | 요금제 없음 |
| `BILLING_METHOD_NOT_FOUND` | 404 | 결제 수단 없음 |
| `BILLING_METHOD_ACCESS_DENIED` | 403 | 타인의 결제 수단 |
| `SUBSCRIPTION_ALREADY_ACTIVE` | 409 | 이미 활성 구독 존재 |
| `SUBSCRIPTION_FIRST_PAYMENT_FAILED` | 402 | 첫 결제 실패 |
| `EXTERNAL_API_FAILED` | 502 | PortOne 정기 결제 API 실패 |

## GET `/api/subscriptions/me`

내 현재 구독 정보를 조회합니다.

- 인증: 필요
- HTTP Status: `200 OK`

### Response Data

```json
{
  "subscriptionId": 100,
  "planId": 1,
  "planName": "BASIC",
  "status": "ACTIVE",
  "monthlyAmount": 9900,
  "nextPaymentDate": "2026-06-29",
  "billingMethod": {
    "billingMethodId": 10,
    "cardCompany": "TOSS",
    "cardMaskedNumber": "1234-****-****-5678"
  },
  "startedAt": "2026-05-29T18:35:00+09:00",
  "canceledAt": null
}
```

구독이 없으면 `data`는 `null`로 반환하거나 `SUBSCRIPTION_NOT_FOUND`를 반환할 수 있습니다. 프론트엔드 처리를 단순하게 하려면 `data: null` 성공 응답을 권장합니다.

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `SUBSCRIPTION_NOT_FOUND` | 404 | 구독 없음. 팀 정책상 성공 `data: null`로 대체 가능 |

## POST `/api/subscriptions/{subscriptionId}/cancel`

활성 구독을 해지합니다. 이미 해지된 구독에 대한 중복 요청은 성공 응답을 반환합니다.

- 인증: 필요
- HTTP Status: `200 OK`

### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reason` | string | N | 해지 사유 |

```json
{
  "reason": "사용 빈도 낮음"
}
```

### Response Data

```json
{
  "subscriptionId": 100,
  "status": "CANCELED",
  "canceledAt": "2026-05-29T19:00:00+09:00",
  "nextPaymentDate": null
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 또는 인증 실패 |
| `SUBSCRIPTION_NOT_FOUND` | 404 | 구독 없음 |
| `SUBSCRIPTION_ACCESS_DENIED` | 403 | 타인의 구독 |

## 정기 결제 스케줄러

공개 API가 아니라 서버 내부 배치입니다.

- 실행 주기: 매일 `00:00` KST
- 대상: `status = ACTIVE`이고 `nextPaymentDate = today`인 구독
- 처리: 구독 청구서 생성, 빌링키 정기 결제 요청, 성공 시 다음 결제일을 1개월 뒤로 갱신
- 중복 방지: `subscription_id + billing_period` UNIQUE 제약을 둡니다.
- UNIQUE 충돌 시 이미 같은 기간 청구가 생성된 것으로 보고 스킵합니다.
- 정기 결제 실패 시 청구서 상태만 `FAILED`로 남기고 구독 상태는 유지합니다. 미납 정책을 도입하면 `PAST_DUE` 전이를 추가합니다.
