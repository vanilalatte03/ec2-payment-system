# Validation Convention

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
