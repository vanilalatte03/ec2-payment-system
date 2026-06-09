# Entity Convention

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

Entity의 조회용 접근자는 Lombok의 `@Getter`를 기본으로 사용한다.

규칙:

- Entity 클래스에는 기본적으로 `@Getter`를 선언한다.
- 필드별 Getter를 직접 작성하지 않는다.
- Setter는 사용하지 않고 의미 있는 상태 변경 메서드로 값을 변경한다.

JPA Entity는 기본 생성자를 `protected`로 제한한다.

권장:

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
```

비권장:

```java
@Entity
public class Payment {

    public Payment() {
    }
}
```
