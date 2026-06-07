# Service Convention

Service는 도메인 로직과 DB 상태 변경 단위를 표현한다.

여러 도메인 Service나 외부 API 호출을 조율하는 복잡한 유스케이스는 Facade에 둔다.
단일 도메인 안에서 끝나는 유스케이스는 Service에 둔다.

권장 메서드명:

```java
confirmPayment(userId, request)
refundPayment(userId, portonePaymentId, request)
createOrder(userId, request)
```

트랜잭션 경계는 Service 계층에 둔다.
외부 API 호출과 내부 DB 트랜잭션 순서를 조율해야 하면 Facade에서 흐름을 나누고, 실제 DB 변경은 Service에 위임한다.

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
Facade 또는 Service는 외부 Client의 응답을 도메인 규칙에 맞게 검증하고, Client는 HTTP 요청/응답 변환에 집중한다.

```
PaymentFacade
PaymentService
PortOneClient
```
