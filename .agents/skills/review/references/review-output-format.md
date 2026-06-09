# Review Output Format

리뷰 결과는 반드시 아래 형식으로 작성한다.

```markdown
# Code Review Result

## Summary

전체적으로 어떤 상태인지 3~5줄로 요약한다.

## Severity Legend

* Critical: 운영 장애, 결제 정합성 훼손, 보안 문제 가능성
* Major: 기능 오류, 유지보수성 저하, 문서 불일치
* Minor: 네이밍, 스타일, 작은 개선점
* Suggestion: 선택적 개선 제안

## Findings

### 1. [Severity] 제목

* 위치:
* 관점:
* 문제:
* 영향:
* 제안:

## Positive Points

의미 있는 장점이 있을 때만 2~5개 작성한다. 핵심 Findings가 흐려질 정도로 억지로 작성하지 않는다.

## Checklist

* [ ] Controller 책임 분리
* [ ] Entity/DTO 분리
* [ ] 공통 예외 처리
* [ ] 트랜잭션 경계 적절성
* [ ] 인증/소유권 검증
* [ ] API 문서 정합성
* [ ] ERD 정합성
* [ ] 결제 멱등성
* [ ] 환불 정합성
* [ ] 웹훅 중복 처리

## Final Verdict

아래 중 하나로 결론을 낸다.

* Approve
* Approve with Comments
* Request Changes
* Blocked
```

## Review Style

리뷰는 직설적으로 작성하되, 비난하지 않는다.

좋은 표현:

* “이 구조는 결제 중복 요청에서 상태 정합성이 깨질 수 있습니다.”
* “현재 구현은 API 문서의 응답 필드와 일치하지 않습니다.”
* “이 로직은 Service 계층으로 이동하는 편이 책임 분리에 더 적합합니다.”
