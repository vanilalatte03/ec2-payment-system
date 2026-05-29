# 인증 API

### 회원가입

회원을 생성합니다. 회원가입 성공 시 기본 장바구니도 함께 생성하는 것을 권장합니다.

- Method: `POST`
- Path: `/api/auth/signup`
- 인증: 불필요
- HTTP Status: `201 Created`

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | string | Y | 로그인 이메일. UNIQUE |
| `password` | string | Y | 비밀번호. 서버에서 암호화 저장 |
| `name` | string | Y | 회원 이름 |
| `phone` | string | Y | 전화번호 |

```json
{
  "email": "customer@example.com",
  "password": "Password123!",
  "name": "홍길동",
  "phone": "010-1234-5678"
}
```

#### Response Data

```json
{
  "userId": 1,
  "email": "customer@example.com",
  "name": "홍길동",
  "phone": "010-1234-5678",
  "createdAt": "2026-05-29T18:30:00+09:00"
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 이메일, 비밀번호, 이름, 전화번호 형식 오류 |
| `EMAIL_ALREADY_EXISTS` | 409 | 이미 가입된 이메일 |

### 로그인

이메일과 비밀번호를 검증하고 JWT access token을 발급합니다.

- Method: `POST`
- Path: `/api/auth/login`
- 인증: 불필요
- HTTP Status: `200 OK`

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | string | Y | 로그인 이메일 |
| `password` | string | Y | 비밀번호 |

```json
{
  "email": "customer@example.com",
  "password": "Password123!"
}
```

#### Response Data

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

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 이메일 또는 비밀번호 누락 |
| `INVALID_LOGIN_CREDENTIALS` | 401 | 이메일 또는 비밀번호 불일치 |

### 로그아웃

로그아웃을 처리합니다. 서버에서 토큰 blocklist를 운영하지 않는다면 클라이언트 토큰 폐기용 성공 응답만 반환해도 됩니다.

- Method: `POST`
- Path: `/api/auth/logout`
- 인증: 필요
- HTTP Status: `200 OK`

#### Request Body

없음

#### Response Data

```json
{
  "loggedOut": true
}
```

#### Errors

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 누락 |
| `INVALID_TOKEN` | 401 | 잘못된 토큰 |
| `EXPIRED_TOKEN` | 401 | 만료된 토큰 |
