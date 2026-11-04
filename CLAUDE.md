@AGENTS.md

## Claude Code 전용

- `.claude/settings.json`의 hook이 Bash·PowerShell 명령을 검사한다. hook을 고치면 AGENTS.md "자주 쓰는 명령"의 hook 테스트를 돌린다
  - `block-pr-merge.mjs`: PR 병합 명령을 막는다. 우회하려고 하지 않는다
  - `check-git-format.mjs`: 브랜치 이름, 커밋 메시지 제목, PR·이슈 제목 형식을 검사한다. 막히면 메시지대로 고쳐서 다시 실행한다
- 커밋·PR·이슈 요청은 `.claude/skills/`의 스킬로 처리한다
  - `git-commit`: 브랜치 확인 후 작업 영역별로 나눠 컨벤션에 맞게 커밋한다
  - `write-pr`: push되지 않은 커밋을 push하고 develop 대상 PR을 연다 (병합은 하지 않는다)
  - `create-issue`: 이슈 양식에 맞춰 제목·라벨·본문을 채워 이슈를 만든다
- 개인 설정은 `.claude/settings.local.json`에 둔다 (git에 올라가지 않는다)
