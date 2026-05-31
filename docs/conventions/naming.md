# Naming Convention

## Class Naming

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| Controller | `{Domain}Controller` | `PaymentController` |
| Service | `{Domain}Service` | `PaymentService` |
| Repository | `{Entity}Repository` | `PaymentRepository` |
| Entity | 명사 단수형 | `Payment` |
| Enum | `{Domain}Status`, `{Domain}Type` | `PaymentStatus` |
| Request DTO | `{Action}{Domain}Request` | `ConfirmPaymentRequest` |
| Response DTO | `{Action}{Domain}Response` | `ConfirmPaymentResponse` |
| Exception | `{Domain}Exception` 또는 `BusinessException` | `PaymentException` |

## Method Naming

메서드명은 행위를 명확히 드러낸다.

권장:

```java
createOrder()
confirmPayment()
refundPayment()
decreaseStock()
restoreUsedPoint()
refreshMembershipGrade()
```

비권장:

```java
process()
handle()
doPayment()
updateData()
check()
```

단, 이벤트 처리처럼 의미가 분명한 경우는 허용한다.

```java
handleWebhook()
```

## Variable Naming

축약어를 남발하지 않는다.

권장:

```java
paymentAmount
orderTotalAmount
currentUserId
usedPointAmount
```

비권장:

```java
payAmt
ordAmt
uid
pt
```
