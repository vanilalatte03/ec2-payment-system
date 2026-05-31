# Repository Convention

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
