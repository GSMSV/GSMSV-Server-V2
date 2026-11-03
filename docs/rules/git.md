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

## 이슈

양식 원본은 `.github/ISSUE_TEMPLATE/`(`task.yml`, `bug.yml`)이다. `gh issue create`로 만들면 이 양식이 적용되지 않으므로 본문을 아래 구조로 직접 쓴다. 빈 이슈는 만들지 않는다.

- 버그(`fix`)는 버그 양식, 그 밖의 타입은 작업·기능 양식을 쓴다
- 라벨은 `--label`로 직접 붙인다. 값은 위 "라벨" 표의 타입별 값이다 (양식의 기본 라벨이 아니라 타입을 따른다)
- 첫 줄에는 양식 맨 위의 안내 문구를 그대로 둔다
- 쓸 내용이 없는 칸은 `없음`이라고 적는다. 칸을 지우지 않는다

작업·기능 (`task.yml`):

```markdown
> 비밀값·내부 IP·포트 같은 운영 정보는 적지 않는다 (공개 저장소).

### 관련 기능

기능 번호와 이름 (예: 23번 자원 이름 규칙). 없으면 없음

### 무엇을

할 일. 항목이 여러 개면 체크리스트(- [ ])로 쓴다

### 완료 조건

이게 되면 끝이라고 할 수 있는 문장 1~2개
```

버그 (`bug.yml`):

```markdown
> 비밀값·내부 IP·포트 같은 운영 정보는 적지 않는다 (공개 저장소).

### 무슨 일이 있었나

실제로 일어난 일. 에러 메시지가 있으면 붙인다 (운영 정보는 지운다)

### 기대한 동작

원래 어떻게 돼야 하는지

### 재현 방법

1. 순서대로 적는다
```

필요하면 마지막에 `### 참고`를 붙여 관련 이슈·선행 작업을 적는다.

```bash
gh issue create --title "feat(vm): VM 생성 API" --label enhancement --body-file issue.md
```

## PR

- base는 `develop`. 본문은 `.github/pull_request_template.md`를 채운다 (`Closes #번호` 포함). 템플릿 맨 위의 HTML 주석은 지운다
- 확인 체크리스트는 해당하는 것만 체크하고, 해당 없는 항목은 그대로 두고 끝에 `해당 없음`을 적는다
- 라벨은 이슈와 같은 규칙으로 `--label`로 붙인다
- **PR은 열기까지만 한다. 병합은 사람이 한다.** `gh pr merge`, `gh api` 병합 호출 모두 금지
- develop을 따라잡을 때는 `git fetch origin && git merge origin/develop` (이건 허용)
