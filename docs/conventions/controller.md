# Controller Convention

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

Controller의 의존성 주입은 `@RequiredArgsConstructor`를 사용한 생성자 주입을 기본으로 한다.

규칙:

- 주입 대상 의존성은 `private final`로 선언한다.
- `@Autowired`를 사용한 필드 주입은 사용하지 않는다.

권장:

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
}
```

비권장:

```java
@RestController
@RequestMapping("/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;
}
```

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
