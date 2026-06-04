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

리뷰 시작 전 문서를 바로 열지 말고 먼저 리뷰 범위를 좁힌다.

현재 브랜치의 변경사항을 리뷰하는 경우 먼저 아래 명령으로 범위를 확인한다.

```shell
git status --short
git diff --stat
git diff --name-only
```

`git diff` 전체 출력은 파일 목록과 통계를 본 뒤 필요한 파일 범위로 좁혀서 확인한다.

특정 파일 리뷰 요청이면 요청된 파일과 직접 관련된 코드와 문서만 읽는다.

파일이 없으면 리뷰를 중단하지 않고 문서 부재를 Findings 또는 Checklist에 명시한다.

## 문서 로딩 규칙

문서는 아래 조건에 맞을 때만 읽는다.

- `README.md`
  - README 변경사항을 리뷰하거나, 실행 방법/프로젝트 범위/환경변수 정합성을 확인해야 할 때
- `docs/CODE_CONVENTION.md`
  - 사용자가 컨벤션 리뷰를 요청했거나, 코드 변경이 Controller/Service/Repository/Entity/DTO/Exception/Validation/Transaction/Logging 같은 컨벤션과 직접 관련될 때
  - 이 파일은 인덱스로만 사용하고, 실제 내용은 관련된 `docs/conventions/*.md`만 개별적으로 읽는다.
- `docs/api/README.md`, `docs/api/{domain}.md`
  - Controller, Request/Response DTO, endpoint, status code, error code, auth 요구사항이 바뀌었거나 문서 정합성 리뷰가 필요할 때
  - `docs/api/*.md` 전체를 한 번에 읽지 않고, 변경 파일명/도메인명/검색 결과로 대상 문서만 고른다.
- `docs/ERD.md`
  - Entity, Repository, DB schema, enum, relation, nullable, unique 조건이 바뀌었거나 ERD 정합성 리뷰가 필요할 때

## 리뷰 기준

리뷰할 코드 범위에 맞는 reference 문서만 읽고 적용한다.
이 섹션의 `references/...` 경로는 워크스페이스 루트가 아니라, 이 `SKILL.md`가 있는 스킬 디렉터리 기준으로 해석한다.

- `references/backend-review-checklist.md`
  - Controller, Service, Repository, Entity, DTO, validation, security, transaction, idempotency를 리뷰할 때
- `references/payment-domain-review-checklist.md`
  - 주문, 결제, 환불, 웹훅, 포인트, 멤버십, 구독 도메인 코드나 문서를 리뷰할 때
- `references/documentation-consistency-checklist.md`
  - 사용자가 문서 정합성을 요청했거나 README/API/ERD 변경사항을 리뷰할 때

관련 없는 reference 문서를 예방 차원에서 읽지 않는다.

## 리뷰 순서

리뷰는 가능한 한 다음 순서로 작성한다.

- 위험도 높은 문제
- 백엔드 정합성 문제
- 문서 불일치
- 컨벤션 문제
- 선택적 개선점

## 결과 형식

기본 결과는 Findings를 먼저 작성한다.

- Findings
- Open Questions 또는 Assumptions
- 간단한 Summary
- Final Verdict

최종 판정은 아래 중 하나를 사용한다.

- `Approve`
- `Approve with Comments`
- `Request Changes`
- `Blocked`

사용자가 기존 상세 템플릿을 명확히 요구하거나 체크리스트까지 포함한 정식 리뷰가 필요할 때만 `references/review-output-format.md`를 읽는다.

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
