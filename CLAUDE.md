@AGENTS.md

## Claude Code 전용

- `.claude/settings.json`의 hook이 Bash·PowerShell 명령을 검사한다
  - `block-pr-merge.mjs`: PR 병합 명령을 막는다. 우회하려고 하지 않는다
  - `check-git-format.mjs`: 브랜치 이름, 커밋 메시지 제목, PR·이슈 제목 형식을 검사한다. 막히면 메시지대로 고쳐서 다시 실행한다
- hook을 고치면 `node --test .claude/hooks/test/hooks.test.mjs`로 확인한다
- 개인 설정은 `.claude/settings.local.json`에 둔다 (git에 올라가지 않는다)
