---
name: git-commit
description: 이 저장소의 깃 컨벤션(docs/rules/git.md)에 맞춰 변경 사항을 커밋한다. 브랜치 이름을 확인하고, 변경을 작업 영역별로 나눠 `타입(scope)!: 한국어 설명` 형식의 커밋을 만든다. "커밋해라", "커밋해줘", "커밋 좀", "변경 사항 저장해", "commit" 등 커밋을 요청받으면 직접 git commit을 치지 말고 항상 이 스킬을 쓴다.
---

# git-commit

변경 사항을 [docs/rules/git.md](../../../docs/rules/git.md) 형식에 맞춰 커밋한다. push와 PR은 하지 않는다 (PR은 `write-pr` 스킬).

규칙이 이 문서와 git.md·CONTRIBUTING.md 사이에서 다르면 CONTRIBUTING.md → git.md → 이 문서 순으로 따른다.

**사용자에게 보내는 모든 텍스트(진행 안내, 질문, 보고)는 한국어로 쓴다.** 사용자 메시지에 한국어 문장이 없어도 마찬가지다. 코드 식별자·명령어는 그대로 둔다.

## 1. 브랜치 확인

```bash
git branch --show-current
```

아래에 해당하면 **커밋하지 않고** 멈춘 뒤 사용자에게 알린다.

| 상황 | 할 일 |
|---|---|
| `develop` 또는 `main` | 이 브랜치에는 커밋하지 않는다. 이슈 번호를 물어 `타입/이슈번호-설명` 브랜치를 만들자고 제안한다. 변경은 working tree에 남아 있으니 `git switch -c <새 브랜치>`로 옮기면 된다 |
| 이름이 `타입/이슈번호-설명` 형식이 아님 (예: `d1ng1724/foo`, `feat/vm-api`) | 커밋하지 않는다. 규칙에 맞는 이름을 제안하고(이슈 번호가 없으면 이슈부터 만들자고 제안), 사용자가 동의하면 `git branch -m <새 이름>`으로 바꾼 뒤 계속한다 |
| detached HEAD (출력이 비어 있음) | 커밋하지 않는다. 사용자에게 상황을 알린다 |

형식: `^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)/[0-9]+-[a-z0-9]+(-[a-z0-9]+)*$`

브랜치 이름의 타입과 이슈 번호는 아래 단계에서 기본값으로 쓴다.

## 2. 변경 사항 파악

```bash
git status --short
git diff --staged --stat
git diff --stat
```

- 바뀐 게 없으면 "커밋할 변경이 없다"고 알리고 끝낸다
- **이미 스테이징된 변경이 있으면** 사용자가 그것만 커밋하려는 것으로 본다. 스테이징된 것만 커밋하고, 스테이징 안 된 변경은 건드리지 않는다. 애매하면 물어본다
- 스테이징된 게 없으면 working tree 전체(untracked 포함)를 대상으로 한다
- 필요한 파일은 `git diff <경로>`로 실제 내용을 읽고 무엇을 왜 바꿨는지 파악한다

커밋 전에 아래를 확인한다. 하나라도 걸리면 커밋하지 않고 사용자에게 알린다.

- **비밀값·내부 IP·포트·서버 주소**가 diff에 없는지 ([security.md](../../../docs/rules/security.md)). `.env`, 키 파일, `application-local.*` 같은 파일이 untracked로 잡혀 있으면 커밋 대상에서 뺀다
- 빌드 산출물(`build/`, `.gradle/`, `out/`)이나 IDE 파일이 섞여 있지 않은지
- `build.gradle.kts`의 의존성 추가·버전 변경이 사용자가 확인한 것인지 (AGENTS.md 절대 규칙)

## 3. 커밋 나누기

한 커밋에는 한 가지 일만 담는다. 변경이 여러 영역에 걸쳐 있으면 영역별로 나눠 여러 커밋을 만든다.

나누는 기준 (위에서부터 우선):

1. **작업 종류가 다르면 나눈다** — 기능 코드(`feat`/`fix`/`refactor`), 빌드 설정(`build`), CI(`ci`), 문서만(`docs`), 포맷만(`style`), 하네스·설정 잡무(`chore`)
2. **도메인 패키지가 다르면 나눈다** — `com.gsmsv.api.vm`과 `com.gsmsv.api.team`을 따로 고쳤으면 커밋도 따로
3. **테스트는 그 테스트가 검증하는 코드와 같은 커밋**에 넣는다. 테스트만 추가·수정했으면 `test`
4. 서로 의존해서 따로 떼면 컴파일이 깨지는 변경은 나누지 않는다

나눌 게 없으면 커밋 하나로 끝낸다. 억지로 쪼개지 않는다. 커밋이 여러 개가 될 때는 어떻게 나눌지 짧게 알려주고 진행한다.

## 4. 커밋 전 검사

Kotlin 파일(`*.kt`, `*.kts`)이 커밋에 포함되면 커밋 전에 반드시 돌린다.

```bash
./gradlew ktlintCheck
```

실패하면 `./gradlew ktlintFormat`으로 고친 뒤 다시 확인하고, 포맷으로 바뀐 파일도 같은 커밋에 넣는다. 고쳐지지 않는 위반은 사용자에게 알리고 멈춘다.
`.claude/hooks/`를 고쳤으면 `node --test .claude/hooks/test/hooks.test.mjs`도 돌린다. 전체 테스트(`./gradlew test`)는 돌리지 않는다 (CI가 한다).

## 5. 커밋 메시지

제목 형식: `타입(scope)!: 한국어 설명`

| 칸 | 규칙 |
|---|---|
| 타입 | `feat fix docs style refactor perf test build ci chore revert` 중 하나. 커밋 내용에 맞춰 고른다 (브랜치 타입과 달라도 된다. 예: `feat` 브랜치 안의 `test` 커밋) |
| `(scope)` | 선택. 도메인 패키지 이름을 그대로 쓴다 (`vm`, `team`, `grn`, `authorization` …). 영어 소문자. 여러 도메인에 걸치거나 도메인이 없으면 뺀다 |
| `!` | 호환성을 깨는 변경(API 계약 변경)에만 붙인다 |
| 설명 | 한국어. 무엇을 했는지 짧게. 끝에 마침표 없음. 콜론 앞은 붙이고 뒤는 한 칸 |

예: `feat(vm): VM 생성 API 추가`, `test(grn): GRN 파서 실패 케이스 추가`, `build: ktlint 도입`, `feat(vm)!: VM 응답 형식 변경`

본문은 선택이다. 제목만으로 이유가 드러나지 않을 때만 빈 줄 뒤에 한국어로 "왜"를 적는다.

## 6. 커밋

커밋마다 해당 파일만 경로를 지정해 스테이징한다. `git add -A`·`git add .`는 쓰지 않는다 (의도하지 않은 파일이 섞인다).

```bash
git add <경로> <경로>
git commit -m "feat(vm): VM 생성 API 추가"
```

본문이 있으면 `-m`을 한 번 더 쓴다: `git commit -m "제목" -m "본문"`

- `.claude/hooks/check-git-format.mjs` hook이 제목 형식을 검사한다. 막히면 메시지대로 제목을 고쳐 다시 커밋한다. hook을 우회하지 않는다
- `--no-verify`, `--amend`(사용자가 요청한 경우 제외), `git reset --hard`, `git stash`는 쓰지 않는다

## 7. 보고

```bash
git log --oneline origin/develop..HEAD
git status --short
```

만든 커밋 목록과 남은 변경(있으면)을 짧게 보고한다. push하지 않는다. PR이 필요하면 `write-pr` 스킬을 쓴다고 안내한다.
