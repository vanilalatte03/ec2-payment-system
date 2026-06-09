# Package Structure Convention

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
