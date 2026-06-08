# 인증 API

인증 API는 회원 계정 생성과 로그인 세션 관리를 담당합니다. 회원가입과 로그인은 인증 없이 호출하고, 로그아웃은 인증된 회원만 호출합니다.

성공/실패 응답은 모두 [공통 응답 wrapper](./common.md#공통-응답)를 사용합니다.

아래 `Response Data` 예시는 wrapper의 `data` 안에 들어가는 값만 보여줍니다.

HTTP 상태 코드는 엔드포인트별 값을 따르지만, 응답 body의 `status`는 공통 wrapper 규칙에 따라 `200`으로 고정됩니다.

## 회원가입

회원을 생성합니다. 회원가입 성공 시 기본 장바구니를 생성하고 가입 축하 포인트 1,000P를 지급합니다.

- Method: `POST`
- Path: `/api/auth/signup`
- 인증: 불필요
- HTTP Status: `201 Created`

### Request Body

| 필드 | 타입     | 필수 | 설명 |
| --- |--------| --- | --- |
| `email` | String | Y | 로그인 이메일. UNIQUE |
| `password` | String | Y | 비밀번호. 서버에서 암호화 저장 |
| `name` | String | Y | 회원 이름 |
| `phone` | String | Y | 전화번호 |

```json
{
  "email": "customer@example.com",
  "password": "Password123!",
  "name": "홍길동",
  "phone": "010-1234-5678"
}
```

### Response Data

```json
{
  "userId": 1,
  "email": "customer@example.com",
  "name": "홍길동",
  "phone": "010-1234-5678",
  "createdAt": "2026-05-29T18:30:00+09:00"
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 이메일 형식 오류, 비밀번호 정책 위반, 이름 또는 전화번호 누락 |
| `EMAIL_ALREADY_EXISTS` | 409 | 이미 가입된 이메일 |

## 로그인

이메일과 비밀번호를 검증하고 JWT access token을 발급합니다.

- Method: `POST`
- Path: `/api/auth/login`
- 인증: 불필요
- HTTP Status: `200 OK`

### Request Body

| 필드 | 타입     | 필수 | 설명 |
| --- |--------| --- | --- |
| `email` | String | Y | 로그인 이메일 |
| `password` | String | Y | 비밀번호 |

```json
{
  "email": "customer@example.com",
  "password": "Password123!"
}
```

### Response Data

```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJhbGciOi...",
  "expiresIn": 3600,
  "user": {
    "userId": 1,
    "email": "customer@example.com",
    "name": "홍길동"
  }
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 이메일 누락 또는 형식 오류, 비밀번호 누락 |
| `INVALID_LOGIN_CREDENTIALS` | 401 | 이메일 또는 비밀번호 불일치 |

## 로그아웃

로그아웃을 처리합니다. 인증된 요청이면 `200 OK`와 함께 로그아웃 성공 여부를 반환합니다.
서버는 별도의 토큰 무효화 저장소를 사용하지 않으며, 클라이언트가 보관 중인 액세스 토큰을 삭제하는 방식으로 로그아웃을 완료합니다.

- Method: `POST`
- Path: `/api/auth/logout`
- 인증: 필요
- HTTP Status: `200 OK`

### Request Body

없음

### Response Data

```json
{
  "loggedOut": true
}
```

### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `INVALID_TOKEN` | 401 | 잘못된 토큰 |
| `EXPIRED_TOKEN` | 401 | 만료된 토큰 |
