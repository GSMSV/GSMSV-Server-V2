# 깃 규칙

원본: [CONTRIBUTING 3~5장](../../.github/CONTRIBUTING.md). Claude Code에서는 hook(`.claude/hooks/check-git-format.mjs`)이 형식을 검사한다.

## 타입

`feat fix docs style refactor perf test build ci chore revert` (11종, CONTRIBUTING 4장)

## 형식

| 대상 | 형식 | 예 |
|---|---|---|
| 브랜치 | `타입/이슈번호-설명` | `feat/12-vm-create-api` |
| 커밋·PR 제목 | `타입(scope)!: 한국어 설명` | `feat(vm): VM 생성 API 추가` |
| 이슈 제목 | `타입(scope): 한국어 설명` | `feat(vm): VM 생성 API` |

- 브랜치 설명은 영어 소문자·숫자, 단어는 하이픈(`-`)으로 잇는다
- **AI가 브랜치를 만들 때 이슈 번호는 필수다.** 이슈가 없으면 사용자에게 이슈를 먼저 만들자고 제안한다
- `(scope)`는 선택이다. 값은 도메인 패키지 이름을 그대로 쓴다 (`vm`, `team` …). 목록은 따로 관리하지 않는다
- `!`는 호환성을 깨는 변경(API 계약 변경)에만 붙인다. 이슈 제목에는 쓰지 않는다
- 콜론 앞은 붙이고 뒤는 한 칸 띄운다. 설명은 한국어, 끝에 마침표 없음
- 한 PR에는 한 가지 일만. PR 제목이 그대로 develop의 squash 커밋 메시지가 된다

## 라벨

| 타입 | 라벨 |
|---|---|
| `feat` | `enhancement` |
| `fix` | `bug` |
| `docs` | `documentation` |
| 나머지 | `chore` |

## PR

- base는 `develop`. 본문은 `.github/pull_request_template.md`를 채운다 (`Closes #번호` 포함)
- **PR은 열기까지만 한다. 병합은 사람이 한다.** `gh pr merge`, `gh api` 병합 호출 모두 금지
- develop을 따라잡을 때는 `git fetch origin && git merge origin/develop` (이건 허용)
