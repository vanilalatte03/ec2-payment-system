---
name: review
description: "사용자가 '리뷰해줘', '코드 리뷰해줘', '백엔드적으로 봐줘', '컨벤션 봐줘', '문서랑 맞는지 봐줘'라고 요청하면 코드 컨벤션, 백엔드 설계 품질, 문서 정합성을 기준으로 리뷰한다."
---

## 역할

요청된 코드 또는 현재 브랜치의 변경사항을 기준으로 코드 리뷰 결과를 작성한다.

리뷰는 코드 컨벤션, 백엔드 설계 품질, 문서 정합성을 중심으로 수행한다.

코드 수정, 리팩토링 패치 작성, 사용자가 요청하지 않은 기능 추가는 하지 않는다.

## 기본 규칙

- 코드를 직접 수정하지 않는다.
- 리팩토링 패치를 작성하지 않는다.
- 전체 구현 코드를 제공하지 않는다.
- 사용자가 요청하지 않은 기능을 추가하지 않는다.
- 프로젝트 범위 밖의 과한 아키텍처를 제안하지 않는다.
- 문제 위치, 영향, 개선 방향, 우선순위를 명확히 작성한다.
- 필요한 경우에만 짧은 예시 코드를 제시한다.
- 문서와 코드가 일치하지 않으면 명확히 지적한다.
- 테스트가 필요한 항목은 별도로 제안한다.

## 확인 절차

리뷰 시작 전 반드시 다음 파일을 확인한다.

- `README.md`
- `docs/CODE_CONVENTION.md`

파일이 없으면 리뷰를 중단하지 않고 문서 부재를 Findings 또는 Checklist에 명시한다.

API 코드가 포함된 경우 다음 파일이 있으면 해당 도메인에 맞는 API 문서를 확인한다.

- `docs/api/README.md`
- `docs/api/*.md`

Entity, Repository, DB 코드가 포함된 경우 다음 파일이 있으면 확인한다.

- `docs/ERD.md`

현재 브랜치의 변경사항을 리뷰하는 경우 먼저 아래 명령으로 범위를 확인한다.

```bash
git status --short
git diff --stat
git diff --name-only
git diff
```

특정 파일 리뷰 요청이면 요청된 파일과 직접 관련된 문서만 읽는다.

## 리뷰 기준

리뷰할 코드 범위에 맞게 아래 reference 문서를 읽고 적용한다.

- `references/backend-review-checklist.md`
  - 공통 백엔드 계층, 검증, 보안, 트랜잭션, 멱등성 기준
- `references/payment-domain-review-checklist.md`
  - 주문, 결제, 환불, 웹훅, 포인트, 멤버십, 구독 도메인 기준
- `references/documentation-consistency-checklist.md`
  - README, API, ERD 문서 정합성 기준

## 리뷰 순서

리뷰는 가능한 한 다음 순서로 작성한다.

- 위험도 높은 문제
- 백엔드 정합성 문제
- 문서 불일치
- 컨벤션 문제
- 선택적 개선점

## 결과 형식

리뷰 결과는 아래 문서를 기준으로 작성한다.

- `references/review-output-format.md`

최종 판정은 아래 중 하나를 사용한다.

- `Approve`
- `Approve with Comments`
- `Request Changes`
- `Blocked`

## 과한 설계 제한

이 프로젝트는 학습용 결제 시스템 프로젝트다.

따라서 다음 제안은 신중하게 한다.

- MSA 전환
- Kafka 도입
- Redis 분산락 도입
- CQRS/Event Sourcing 도입
- 복잡한 DDD 구조 강제
- 필수 범위를 넘는 복잡한 환불 프레임워크
- 다중 PG 추상화
- 운영급 모니터링 시스템 구축

필요성이 명확하지 않다면 단순한 Spring Boot + JPA + MySQL 구조 안에서 해결책을 제안한다.
