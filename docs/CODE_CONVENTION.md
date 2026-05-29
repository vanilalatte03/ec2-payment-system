# Code Convention

## 1. Package Structure

기본 구조는 **도메인 중심 패키지 구조**를 권장한다.

```
domain
 └─ payment
     ├─ controller
     ├─ service
     ├─ repository
     ├─ entity
     ├─ dto
     └─ exception
```

예시:

```
com.example.payment
 ├─ global
 │   ├─ config
 │   ├─ exception
 │   ├─ response
 │   └─ security
 └─ domain
     ├─ user
     ├─ product
     ├─ order
     ├─ payment
     ├─ point
     ├─ membership
     └─ subscription
```

도메인별 내부 구조:

```
domain/payment
 ├─ controller
 ├─ service
 ├─ repository
 ├─ entity
 ├─ dto
 └─ client
```

`global`에는 특정 도메인에 속하지 않는 공통 설정만 둔다.

예시:

- Security 설정
- 공통 예외
- 공통 응답
- Jackson 설정
- Auditing 설정

---

## 2. Naming Convention

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

---

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

---

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

---

## 3. DTO Convention

Controller는 Entity를 직접 받거나 반환하지 않는다.

금지:

```java
@PostMapping("/orders")
public Order createOrder(@RequestBody Order order)
```

권장:

```java
@PostMapping("/orders")
public CreateOrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request)
```

Request/Response DTO는 역할을 분리한다.

```
CreateOrderRequest
CreateOrderResponse
ConfirmPaymentRequest
ConfirmPaymentResponse
RefundPaymentRequest
RefundPaymentResponse
```

DTO는 가능하면 `record`를 사용한다.

```java
public record ConfirmPaymentRequest(
    @NotNull Long orderId,
    @NotBlank String portonePaymentId
) {
}
```

단, JPA Entity는 `record`로 만들지 않는다.

---

## 4. Entity Convention

Entity는 DB 매핑과 도메인 상태 변경 책임만 가진다.

Entity에 넣지 않는다:

- 외부 API 호출 로직
- Controller 응답 생성 로직
- 복잡한 유스케이스 orchestration
- 다른 도메인의 비즈니스 흐름

Setter 남발을 피하고 의미 있는 상태 변경 메서드를 사용한다.

권장:

```java
payment.markAsPaid();
payment.markAsRefunded();
order.complete();
order.refund();
```

비권장:

```java
payment.setStatus(PaymentStatus.PAID);
order.setStatus(OrderStatus.COMPLETED);
```

Entity 생성은 정적 팩토리 메서드 또는 생성자를 사용한다.

```java
Payment.createReadyPayment(order, portonePaymentId, amount);
```

---

## 5. Controller Convention

Controller는 요청/응답 처리만 담당한다.

Controller의 책임:

- Request DTO 검증
- 인증 사용자 식별
- Service 호출
- Response DTO 반환

Controller에서 하지 않는다:

- 비즈니스 로직 처리
- Entity 직접 조립
- 외부 API 호출
- 트랜잭션 처리
- 복잡한 조건 분기

권장:

```java
@PostMapping("/payments/confirm")
public ApiResponse<ConfirmPaymentResponse> confirmPayment(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody ConfirmPaymentRequest request
) {
    return ApiResponse.success(paymentService.confirmPayment(userDetails.getUserId(), request));
}
```

---

## 6. Service Convention

Service는 유스케이스를 표현한다.

권장 메서드명:

```java
confirmPayment(userId, request)
refundPayment(userId, portonePaymentId, request)
createOrder(userId, request)
```

트랜잭션 경계는 Service 계층에 둔다.

조회 전용:

```java
@Transactional(readOnly = true)
```

상태 변경:

```java
@Transactional
```

외부 API 연동은 별도 Client 클래스로 분리한다.

```
PaymentService
PortOneClient
```

---

## 7. Repository Convention

Repository는 DB 접근만 담당한다.

규칙:

- Repository에서 비즈니스 정책을 처리하지 않는다.
- 단건 조회 실패 가능성이 있으면 `Optional`을 사용한다.
- 복잡한 조회는 명확한 메서드명 또는 JPQL을 사용한다.

권장:

```java
Optional<Payment> findByPortonePaymentId(String portonePaymentId);
boolean existsByPortonePaymentId(String portonePaymentId);
```

---

## 8. Exception Convention

예외 응답은 공통 형식을 사용한다.

권장 구조:

```
ErrorCode
BusinessException
GlobalExceptionHandler
```

에러 코드는 도메인별로 명확히 관리한다.

예시:

```
USER_NOT_FOUND
ORDER_NOT_FOUND
ORDER_ACCESS_DENIED
PAYMENT_NOT_FOUND
PAYMENT_ALREADY_PROCESSED
PAYMENT_AMOUNT_MISMATCH
INSUFFICIENT_POINT
INVALID_ORDER_STATUS
```

비즈니스 예외는 직접 `RuntimeException`을 던지지 않고 공통 예외를 사용한다.

권장:

```java
throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
```

비권장:

```java
throw new RuntimeException("금액이 다릅니다.");
```

---

## 9. API Response Convention

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

---

## 10. Validation Convention

Request DTO에 기본 검증을 적용한다.

예시:

```java
public record CreateOrderRequest(
    @NotEmpty List<OrderItemRequest> items,
    @PositiveOrZero Integer usedPoint
) {
}
```

검증 대상:

- 빈 상품 목록
- 0 이하 수량
- 음수 금액
- 음수 포인트
- 빈 portonePaymentId
- 잘못된 Enum 값
- 필수 값 누락

---

## 11. Transaction Convention

트랜잭션은 Service 계층에서 시작한다.

주의 사항:

- 외부 API 호출과 DB 트랜잭션 범위를 신중하게 잡는다.
- 결제 성공 후 내부 처리 실패 가능성을 고려한다.
- 결제/환불은 멱등성을 고려한다.
- 읽기 전용 메서드는 `readOnly = true`를 사용한다.

결제 도메인에서는 다음을 특히 확인한다.

- 결제 확정 중복 처리
- 환불 중복 처리
- Webhook 중복 처리
- 외부 결제 성공 후 내부 실패 시 보상 트랜잭션

---

## 12. Logging Convention

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

---

## 13. Test Convention

테스트명은 상황과 기대 결과가 드러나게 한국어로 작성한다.

권장:

```java
void 결제_금액과_주문_금액이_다르면_결제_확정에_실패한다
```

테스트 우선순위:

1. 결제 확정 성공
2. 결제 금액 불일치
3. 중복 결제 확정
4. 환불 성공
5. 중복 환불
6. 포인트 부족
7. 소유권 검증 실패
8. Webhook 중복 수신