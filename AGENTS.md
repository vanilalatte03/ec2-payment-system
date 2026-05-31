# AGENTS.md

## 프로젝트 규칙

- `.agents/skills`는 이름/설명으로 먼저 확인하고, 요청과 맞는 `SKILL.md`만 읽어 적용한다.
- 문서는 `rg`, `git diff --name-only`, `git diff --stat` 등으로 범위를 좁힌 뒤 필요한 파일만 읽는다. `docs/**/*.md`, `docs/api/*.md`, `references/*.md`를 한 번에 읽지 않는다.
- `docs/CODE_CONVENTION.md`는 인덱스로만 보고, 필요한 `docs/conventions/*.md`만 읽는다. 테스트 작업은 프로젝트 테스트 스킬을 따른다.
- OS별 명령어가 다르면 Windows PowerShell과 macOS/Linux를 함께 적는다. Gradle wrapper는 Windows `.\gradlew.bat test`, macOS/Linux `./gradlew test`를 사용한다.
- 셸별 줄 연결 문자가 다른 여러 줄 CLI 명령은 가능하면 한 줄로 적는다.
- 시스템/개발자/보안 지침이나 사용자 명시 요청과 충돌하면 그 지침을 우선한다.
