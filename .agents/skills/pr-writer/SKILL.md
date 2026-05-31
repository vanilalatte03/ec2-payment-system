---
name: pr-writer
description: "사용자가 'pr 작성해줘', 'pr 올려줘', 'PR 만들어줘' 라고 요청하면 현재 브랜치의 변경사항을 분석해 PR 제목/본문을 작성하고, 명확히 요청된 경우 GitHub CLI로 PR을 생성한다."
---

## 역할

현재 브랜치의 실제 커밋과 변경사항을 기준으로 GitHub Pull Request 제목과 본문을 작성한다.

사용자가 PR 생성까지 명확히 요청한 경우에만 `gh pr create`로 PR을 생성한다.

코드 수정, 커밋, push는 하지 않는다.

## 기본 규칙

- 실제 커밋, 변경 파일, diff에 근거해서만 작성한다.
- 구현되지 않은 내용, 향후 계획, 추측은 포함하지 않는다.
- 확인하지 않은 테스트나 수동 검증을 완료로 표시하지 않는다.
- base 브랜치는 별도 지시가 없으면 `origin/develop`을 우선 사용한다.
- `origin/develop`이 없으면 `origin/main`, `origin/master` 순서로 사용한다.
- 현재 브랜치가 원격에 push되어 있지 않으면 PR을 생성하지 않고 push가 필요하다고 안내한다.
- 사용자가 PR 생성을 명확히 요청하지 않았다면 PR 제목과 본문 초안만 제공한다.

## 확인 절차

먼저 아래 명령으로 현재 브랜치와 저장소 상태를 확인한다.

```shell
git branch --show-current
git status --short
git log --oneline --decorate -n 10
git remote -v
git branch -r
```

base 브랜치를 선택한 뒤 아래 명령으로 변경사항을 확인한다.

```shell
git diff --stat <base>...HEAD
git diff --name-only <base>...HEAD
```

전체 diff는 파일 목록과 통계만으로 PR 내용을 충분히 파악할 수 없을 때만 확인한다.
확인이 필요하면 `git diff <base>...HEAD -- <file>`처럼 변경 파일 단위로 좁혀서 읽는다.

필요한 경우 변경된 파일이나 직접 관련된 문서만 읽는다.

아래 경로는 전체 열람 대상이 아니라 검색 후보이다. 먼저 변경 파일, 파일명, 검색 결과로 범위를 좁힌 뒤 필요한 문서만 개별적으로 읽는다.

- `README.md`
- `docs/api/README.md`
- `docs/api/*.md`
- `docs/ERD.md`
- `docs/**/*.md`

`docs/**/*.md` 전체를 한 번에 읽지 않는다.

## PR 제목

변경의 핵심 성격에 맞는 태그 하나를 사용한다.

- `[feat]`: 기능 추가
- `[fix]`: 버그 수정
- `[refactor]`: 구조 개선
- `[docs]`: 문서 수정
- `[test]`: 테스트 추가 또는 수정
- `[chore]`: 설정, 빌드, 유지보수

예시:

```text
[feat] 고객 조회 기능 구현
[fix] 주문 상태 필터링 오류 수정
[refactor] 상품 조회 로직 구조 개선
```

## PR 본문 템플릿

```markdown
## 작업 내용

- 

## 변경 이유

- 

## 주요 변경 사항

- 

## 테스트 및 확인

- [ ] 

## 리뷰 포인트

- 

## 참고 사항

- 
```

실제로 확인한 항목은 명령어와 결과를 함께 적고 체크한다.

```markdown
- [x] `.\gradlew.bat test` 통과
- [x] `./gradlew test` 통과
```

테스트 명령은 실행한 OS에 맞는 실제 명령만 적는다.

- Windows PowerShell: `.\gradlew.bat test`
- macOS/Linux: `./gradlew test`

확인하지 않은 항목은 체크하지 않는다.

```markdown
- [ ] 로컬 테스트 실행 필요
- [ ] API 요청/응답 확인 필요
```

## PR 생성

사용자가 "pr 올려줘", "PR 만들어줘", "생성해줘"처럼 PR 생성을 명확히 요청한 경우에만 생성한다.

여러 줄 명령은 셸별 줄 연결 문자가 달라질 수 있으므로 기본적으로 한 줄로 실행한다.

```shell
gh pr create --base <base-branch-name> --head <current-branch-name> --title "<title>" --body "<body>"
```

`gh`가 설치되어 있지 않거나 인증되지 않은 경우, 또는 현재 브랜치가 push되어 있지 않은 경우에는 PR 제목과 본문을 제공하고 자동 생성이 불가능한 이유를 설명한다.
