# Facade Convention

Facade는 여러 도메인 Service와 외부 Client 호출을 조율하는 유스케이스 계층이다.

사용 기준:

- 외부 API 호출과 내부 DB 트랜잭션 순서를 분리해야 할 때
- 결제 확정처럼 보상 처리, 멱등 처리, 웹훅 재처리가 필요한 흐름일 때
- Controller가 여러 Service를 직접 호출하게 될 때
- 하나의 유스케이스가 주문, 결제, 포인트, 재고, 장바구니처럼 여러 도메인을 함께 조율할 때

권장 구조:

```java
@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    public ConfirmPaymentResponse confirmPayment(Long userId, ConfirmPaymentRequest request) {
        // 외부 API 호출과 내부 트랜잭션 순서를 조율한다.
    }
}
```

규칙:

- Controller는 복잡한 유스케이스에서 Facade를 호출할 수 있다.
- Facade는 전체 흐름과 분기, 외부 API 호출, 보상 처리 순서를 담당한다.
- Facade는 Repository를 직접 참조하지 않는다.
- DB 상태 조회와 변경은 Service에 위임한다.
- 외부 API 호출을 포함하는 Facade 메서드에는 기본적으로 `@Transactional`을 붙이지 않는다.
- Facade와 Service 사이 전달 객체는 엔티티 대신 필요한 값만 담은 record 또는 DTO를 사용한다.

네이밍:

- 도메인 전체 조율: `PaymentFacade`
- 특정 유스케이스 조율: `PaymentConfirmFacade`, `RefundFacade`
- 도메인 Service: `PaymentService`, `RefundService`

비권장:

```java
@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentRepository paymentRepository;
    private final PortoneClient portoneClient;

    @Transactional
    public void confirmPayment(...) {
        // 외부 API 호출과 DB 변경을 같은 트랜잭션에 묶지 않는다.
    }
}
```
