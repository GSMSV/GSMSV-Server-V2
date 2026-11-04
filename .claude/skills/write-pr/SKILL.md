---
name: write-pr
description: 이 저장소의 깃 컨벤션(docs/rules/git.md)에 맞춰 develop 대상 PR을 연다. 브랜치를 확인하고, push되지 않은 커밋은 push한 뒤, PR 제목·본문(.github/pull_request_template.md, Closes #번호)·라벨을 규칙대로 채운다. 병합은 하지 않는다. "PR 작성해라", "PR 올려줘", "PR 만들어", "풀리퀘 열어", "pull request" 등 PR을 요청받으면 직접 gh pr create를 치지 말고 항상 이 스킬을 쓴다.
---

# write-pr

현재 브랜치로 `develop` 대상 PR을 연다. 규칙 원본은 [docs/rules/git.md](../../../docs/rules/git.md)와 [CONTRIBUTING 3~5장](../../../.github/CONTRIBUTING.md)이다.

**PR은 열기까지만 한다. 병합은 사람이 한다.** `gh pr merge`(`--auto` 포함), `gh api`로 병합 API를 부르는 것 모두 금지다. `.claude/hooks/block-pr-merge.mjs`가 막으며, 우회하지 않는다.

**사용자에게 보내는 모든 텍스트(진행 안내, 질문, 보고)는 한국어로 쓴다.** 사용자 메시지에 한국어 문장이 없어도 마찬가지다. 코드 식별자·명령어는 그대로 둔다.

## 1. 브랜치 확인

```bash
git branch --show-current
```

| 상황 | 할 일 |
|---|---|
| `develop` 또는 `main` | PR을 만들지 않는다. 작업 브랜치에서 하도록 안내한다 |
| 이름이 `타입/이슈번호-설명` 형식이 아님 | PR을 만들지 않는다. 규칙에 맞는 이름을 제안한다 (이슈가 없으면 이슈부터). 이미 원격에 push된 브랜치면 이름 변경이 원격 브랜치를 새로 만든다는 점도 알린다 |

브랜치 이름에서 **타입**과 **이슈 번호**를 뽑는다. `chore/16-git-commit-write-pr` → 타입 `chore`, 이슈 `16`.

## 2. 커밋 상태 확인

```bash
git fetch origin
git status --short
git log --oneline origin/develop..HEAD
```

- **커밋되지 않은 변경이 있으면** PR에 들어가지 않는다고 알리고, `git-commit` 스킬로 먼저 커밋할지 사용자에게 묻는다
- **`origin/develop..HEAD`에 커밋이 없으면** PR을 만들 게 없다. 알리고 끝낸다
- develop이 앞서 나가 충돌이 예상되면 `git merge origin/develop`으로 따라잡을지 사용자에게 묻는다 (rebase·force push는 하지 않는다)

## 3. 이미 열린 PR 확인

```bash
gh pr list --head <브랜치> --state open --json number,url,title,baseRefName
```

열린 PR이 있으면 새로 만들지 않는다. 4단계(push)만 하고 PR 링크를 알려준다. 제목·본문 갱신이 필요해 보이면 사용자에게 묻고 `gh pr edit`으로 고친다. base가 `develop`이 아니면 그 사실을 알린다.

## 4. push

```bash
git status -sb   # 첫 줄에서 upstream과 ahead 여부를 본다
```

- upstream이 없으면: `git push -u origin <브랜치>`
- upstream이 있고 ahead면: `git push`
- 이미 최신이면 push하지 않는다
- push가 거절되면(원격이 앞서 있음) force push하지 않는다. 상황을 알리고 멈춘다

`develop`·`main`에는 절대 push하지 않는다.

## 5. 내용 모으기

```bash
gh issue view <이슈번호> --json title,body,labels,state
git log --format='%s%n%b' origin/develop..HEAD
git diff --stat origin/develop...HEAD
```

이슈가 없거나 닫혀 있으면 사용자에게 알리고 계속할지 묻는다.

## 6. 제목

형식: `타입(scope)!: 한국어 설명` (커밋 제목과 같은 규칙)

- 타입은 **브랜치 타입**을 쓴다
- 이슈 제목이 같은 형식이면 그것을 바탕으로 하되, 실제 변경을 설명하는 동사형으로 다듬는다 (예: 이슈 `feat(vm): VM 생성 API` → PR `feat(vm): VM 생성 API 추가`)
- `(scope)`는 도메인 패키지 이름. 없거나 여러 개면 뺀다
- `!`는 API 계약을 깨는 변경에만
- 한 PR에는 한 가지 일만. 제목에 "그리고"가 필요하면 PR을 나누자고 제안한다
- **PR 제목이 그대로 develop의 squash 커밋 메시지가 된다**

## 7. 본문

`.github/pull_request_template.md`를 읽어 채운다. 템플릿이 바뀌었을 수 있으니 매번 파일을 읽는다.

- 맨 위 HTML 주석(`<!-- ... -->`)은 지운다. `## 관련 이슈` 아래 주석도 지운다
- **Summary**: `- **키워드**: 무엇을 바꿨는지` 형식으로 변경 묶음마다 한 줄. 커밋 목록과 diff를 근거로 쓴다
- **Test plan**: 실제로 실행한 확인만 적는다 (예: `./gradlew ktlintCheck`, `./gradlew test --tests '...'`, `node --test .claude/hooks/test/hooks.test.mjs`). 실행해서 통과한 것은 `- [x]`, 리뷰어가 할 확인이나 CI에 맡기는 것은 `- [ ]`. 돌리지 않은 테스트를 통과했다고 쓰지 않는다
- **관련 이슈**: `Closes #<이슈번호>`. 이슈가 없으면 줄을 지운다
- **확인**: 해당하고 지킨 항목만 `- [x]`. 이번 변경과 관계없는 항목은 체크하지 않고 그대로 둔 채 줄 끝에 ` 해당 없음`을 붙인다. 운영 정보 항목은 diff를 확인한 뒤 체크한다
- 한국어로 쓴다. 비밀값·내부 IP·포트·서버 주소는 쓰지 않는다

본문은 저장소 밖 임시 파일에 쓰고 `--body-file`로 넘긴다 (저장소 안에 파일을 만들면 커밋에 섞일 수 있다).

## 8. 라벨

| 타입 | 라벨 |
|---|---|
| `feat` | `enhancement` |
| `fix` | `bug` |
| `docs` | `documentation` |
| 나머지 | `chore` |

## 9. PR 만들기

```bash
gh pr create --base develop --head <브랜치> \
  --title "feat(vm): VM 생성 API 추가" \
  --label enhancement \
  --body-file <임시 본문 파일>
```

- `--base develop`은 항상 명시한다
- 사용자가 "작업 중", "draft"라고 하면 `--draft`를 붙인다
- `check-git-format.mjs` hook이 제목을 검사한다. 막히면 메시지대로 고쳐 다시 실행한다

만든 뒤 확인한다.

```bash
gh pr view --json url,baseRefName,title,labels
```

base가 `develop`이 아니면 `gh pr edit --base develop`으로 고친다.

## 10. 보고

PR 링크, 제목, 라벨을 알려주고 **병합은 사람이 해야 한다**고 요청한다. develop 병합에는 CI 통과와 작성자가 아닌 1명의 승인이 필요하다는 점도 덧붙인다. 병합하지 않는다.
