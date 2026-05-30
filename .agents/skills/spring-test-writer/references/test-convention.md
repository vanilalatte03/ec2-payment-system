# Test Convention

## 기본 환경

- Spring Boot 4.0.6
- Java 21
- Gradle
- JUnit Platform

테스트 실행 명령은 Windows 기준 `.\gradlew.bat test`를 안내한다.

## 메서드명

테스트 메서드명은 상황과 기대 결과가 드러나게 한국어로 작성한다.

권장 형식:

```java
@Test
void 대상_조건_기대결과() {
    // given

    // when

    // then
}
```

예시:

```java
@Test
void 결제확정_결제금액과주문금액이다르면_실패한다() {
    // given

    // when

    // then
}
```

## 테스트 본문

- `// given`, `// when`, `// then` 주석으로 구분한다.
- 검증은 AssertJ 스타일을 우선 사용한다.
- 필요하면 JUnit 5와 Mockito를 사용한다.
- 실패 메시지를 감추기보다 기대 상태와 실제 상태가 명확히 드러나게 검증한다.

## 테스트 계층 선택

- Service 테스트는 핵심 비즈니스 로직을 검증한다.
    - DB, 외부 API, 다른 Service 등 테스트 대상이 아닌 의존성은 필요하면 Mock으로 대체한다.

- Controller 테스트는 API 요청과 응답을 검증한다.
    - URL, HTTP Method, 요청 Body 검증, 응답 Status, 응답 JSON 구조를 확인한다.
    - 필요하면 MockMvc 기반 테스트를 사용한다.

- Repository 테스트는 직접 작성한 쿼리나 조회 조건을 검증할 때 작성한다.
    - 단순 JpaRepository 기본 기능은 우선순위를 낮춘다.
    - 필요하면 @DataJpaTest를 사용한다.

- 여러 계층이 함께 동작해야 검증 가능한 핵심 흐름은 통합 테스트를 고려한다.
    - 예: 결제 확정, 환불, 포인트 사용/적립, 웹훅 중복 처리, 트랜잭션 롤백

## 우선순위

- 돈, 상태, 소유권, 멱등성, 트랜잭션 정합성이 바뀌는 테스트를 우선 작성한다.
- 단순 getter/setter 테스트는 우선 작성하지 않는다.
- 의미 없는 CRUD 테스트는 후순위로 둔다.
- 테스트가 프로덕션 코드 결함을 드러내면 프로덕션 코드를 임의로 수정하지 말고 원인과 수정 제안을 별도로 설명한다.
