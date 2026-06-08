# EC2 Payment System

커머스 주문부터 결제, 포인트, 환불까지 이어지는 결제 도메인을 구현한 Spring Boot 기반 백엔드 프로젝트입니다. 주문 생성 시 재고를 선차감하고, PortOne 연동을 통해 카드 결제와 포인트 복합 결제를 처리하며, 웹훅과 클라이언트 결제 확정 요청이 중복되거나 순서가 바뀌어도 동일한 최종 상태로 수렴하도록 설계합니다.

## 주요 기능

- JWT 기반 회원 인증과 소유권 검증
- 상품 조회, 장바구니, 주문 생성 및 재고 선차감
- PortOne 카드 결제, 포인트 결제, 복합 결제
- 결제 확정 API와 웹훅의 멱등 처리
- 포인트 원장 기반 적립/사용/복구 관리
- 포인트/PG 결제 비율 기반 부분 환불

## 향후 확장 고려 사항

- 멤버십 등급 및 구독 결제 확장 설계

## 기술 스택

![Java](https://img.shields.io/badge/Java-21-007396?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)

## 문서

- [ERD](./docs/ERD.md)
- [API 명세](./docs/api/README.md)
- [코드 컨벤션](./docs/CODE_CONVENTION.md)
- [더미 데이터](./docs/DUMMY_DATA.md)

## 실행 방법

`.env.example` 파일을 복사해 `.env` 파일을 만들고, 로컬 환경에 맞게 값을 수정합니다.

```bash
cp .env.example .env
```

MySQL에 `ec2_payment_system` 데이터베이스를 생성한 뒤 애플리케이션을 실행합니다.

```bash
./gradlew bootRun
```

Windows:

```powershell
.\gradlew.bat bootRun
```

실행 후 다음 주소에서 정적 화면을 확인할 수 있습니다.

- `http://localhost:8080`
- `http://localhost:8080/payment.html`

## 테스트

```bash
./gradlew test
```

Windows:

```powershell
.\gradlew.bat test
```
