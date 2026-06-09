# Service Convention

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

Service의 의존성 주입은 `@RequiredArgsConstructor`를 사용한 생성자 주입을 기본으로 한다.

규칙:

- 주입 대상 의존성은 `private final`로 선언한다.
- `@Autowired`를 사용한 필드 주입은 사용하지 않는다.

권장:

```java
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PortOneClient portOneClient;
}
```

비권장:

```java
@Service
public class PaymentService {

    @Autowired
    private PaymentRepository paymentRepository;
}
```

외부 API 연동은 별도 Client 클래스로 분리한다.

```
PaymentService
PortOneClient
```
