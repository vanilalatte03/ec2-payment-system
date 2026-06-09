# Exception Convention

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
