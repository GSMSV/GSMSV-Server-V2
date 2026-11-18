---
name: resolve-reviews
description: 현재 브랜치 PR의 해결되지 않은 리뷰 스레드를 가져와 하나씩 맞는지 검증하고, 맞는 리뷰는 반영·커밋·push한 뒤 반영했다고 답하고, 틀린 리뷰는 반영하지 않는 이유를 답한 다음 처리한 스레드를 resolve한다. "리뷰 반영해", "리뷰 처리해", "코멘트 해결해", "리뷰 확인하고 고쳐", "resolve" 등 PR 리뷰를 처리해 달라는 요청이면 항상 이 스킬을 쓴다.
---

# resolve-reviews

현재 브랜치 PR의 **해결되지 않은 리뷰 스레드**를 처리한다. 리뷰가 맞는지는 리뷰어 말이 아니라 지금 코드, [AGENTS.md](../../../AGENTS.md), 계약으로 판단한다. 커밋은 `git-commit` 스킬로 하고, PR 병합·리뷰 dismiss·재리뷰 요청은 하지 않는다.

## 1. PR 확인

```bash
git branch --show-current
git status --short
gh pr view --json number,url,state,baseRefName,headRefName,isDraft
gh repo view --json owner,name -q '.owner.login + "/" + .name'
```

| 상황 | 할 일 |
|---|---|
| `develop`·`main`에 있음, 또는 이 브랜치의 PR이 없음 | 멈추고 알린다. 사용자가 PR 번호를 주면 그 PR의 브랜치로 옮길지 묻는다 |
| PR이 `OPEN`이 아님 | 멈추고 알린다 |
| 커밋되지 않은 변경이 있음 | 리뷰 반영과 섞이므로 멈추고, 먼저 `git-commit`으로 커밋할지 묻는다 |
| 원격 브랜치가 앞서 있음 (`git status -sb`의 behind) | `git pull --ff-only`. 안 되면 멈추고 알린다 (force push·rebase 금지) |

## 2. 리뷰 가져오기

해결되지 않은 스레드만 가져온다. `<owner>`, `<repo>`, `<번호>`는 1단계 값이다.

```bash
gh api graphql -F owner=<owner> -F repo=<repo> -F number=<번호> -f query='
query($owner: String!, $repo: String!, $number: Int!) {
  repository(owner: $owner, name: $repo) {
    pullRequest(number: $number) {
      reviewThreads(first: 100) {
        nodes {
          id isResolved isOutdated path line originalLine
          comments(first: 50) {
            nodes { databaseId author { login } body url createdAt }
          }
        }
      }
    }
  }
}' --jq '.data.repository.pullRequest.reviewThreads.nodes[] | select(.isResolved | not)'
```

- 쿼리에 `merge`가 들어간 필드를 넣지 않는다. settings.json의 `gh api *merge*` 거부 규칙과 병합 차단 hook에 걸린다
- 스레드의 **첫 댓글이 리뷰 내용**이고, 뒤 댓글은 그 뒤의 대화다. 이미 누군가 답했고 리뷰어가 받아들였다면 그 흐름까지 읽고 판단한다
- 스레드가 아닌 리뷰 본문(요약 리뷰)도 읽는다: `gh pr view <번호> --json reviews --jq '.reviews[] | select(.body != "") | {author: .author.login, state, body}'`. resolve할 대상은 아니지만, 고칠 내용이 있으면 스레드와 같은 방식으로 판정해 보고에 넣는다
- 해결되지 않은 스레드가 없으면 그렇게 알리고 끝낸다

## 3. 검증

스레드마다 아래 순서로 확인하고 판정한다. 리뷰어가 사람이든 봇이든 같은 기준을 쓴다.

1. **지금 코드를 연다.** 리뷰가 달린 `path`의 `line` 주변을 HEAD 기준으로 읽는다. `isOutdated`면 줄 번호가 바뀌었으니 `originalLine`과 리뷰 본문으로 위치를 다시 찾는다
2. **주장을 확인한다.** "버그다"라면 실제로 그 입력에서 틀리는지 코드 흐름을 따라가 본다. 필요하면 관련 테스트를 돌리거나 실패하는 테스트를 먼저 써 본다. 규칙 위반이라는 주장은 AGENTS.md·계약의 해당 줄을 찾는다
3. **판정한다**

| 판정 | 기준 | 처리 |
|---|---|---|
| 반영 | 주장이 맞고 이 PR 범위 안이다 | 4단계에서 고친다 |
| 이미 반영됨 | 이후 커밋에서 이미 고쳐졌다 (outdated 스레드에 흔하다) | 고친 커밋 해시를 찾아 답한다 |
| 반영 안 함 | 주장이 틀렸다, 규칙·계약과 맞지 않는다, 취향 차이다 | 근거(파일:줄, 규칙 문서)를 들어 답한다 |
| 범위 밖 | 맞는 지적이지만 이 PR의 일이 아니다 (AGENTS.md 1.2 "한 PR 한 가지 일") | 반영하지 않고, 후속 이슈를 만들지 사용자에게 묻는다 |
| 사용자 판단 | 설계 선택, API·계약 변경, 의존성 추가, 판단 근거가 부족함 | 혼자 정하지 않는다 |

- 리뷰를 따르면 AGENTS.md나 계약을 어기게 되면 반영하지 않는다. 규칙이 리뷰보다 앞선다
- 확신이 없으면 "반영 안 함"이 아니라 "사용자 판단"으로 둔다

판정이 끝나면 고치기 전에 표로 보여준다. **사용자 판단**이나 **범위 밖**이 하나라도 있으면 답을 받은 뒤 진행한다. 모두 반영·이미 반영됨·반영 안 함이면 표를 보여주고 바로 진행한다.

| # | 위치 | 리뷰어 | 요지 | 판정 | 근거 |
|---|---|---|---|---|---|
| 1 | `VmService.kt:42` | 리뷰어 | null 체크 누락 | 반영 | 삭제된 VM 조회 시 NPE |

## 4. 반영

- 반영할 리뷰를 고친다. 리뷰 하나를 고치려고 관계없는 코드까지 바꾸지 않는다
- 확인: `./gradlew compileKotlin compileTestKotlin ktlintCheck`와 관련 테스트(AGENTS.md 4.1). 버그 지적이면 그 경우를 잡는 테스트를 함께 넣는다. hook·스킬을 고쳤으면 AGENTS.md "자주 쓰는 명령"의 해당 검사를 돌린다
- 실패하면 고친 뒤 다시 돌린다. 리뷰를 반영할 수 없다고 판단이 바뀌면 판정을 바꾸고 사용자에게 알린다
- `git-commit` 스킬로 커밋한다. 커밋 타입은 내용대로 고른다 (`fix`, `refactor`, `test`, `docs` …). 관계없는 리뷰끼리는 커밋을 나눈다
- `git push`한다. force push하지 않는다

## 5. 답변

push가 끝난 뒤에 답한다 (답변에 적을 커밋 해시가 원격에 있어야 한다). 스레드의 첫 댓글(`databaseId`)에 답글을 단다. 본문은 저장소 밖 임시 파일에 쓴다.

```bash
gh api repos/{owner}/{repo}/pulls/<번호>/comments/<databaseId>/replies -F body=@<임시 본문 파일>
```

답변은 한국어로, 2~4줄로 쓴다.

| 판정 | 답변 |
|---|---|
| 반영 | `반영했습니다 (<짧은 해시>).` + 무엇을 어떻게 바꿨는지 한 줄 + 추가한 테스트가 있으면 이름 |
| 이미 반영됨 | `<짧은 해시>에서 이미 반영됐습니다.` + 어떻게 바뀌었는지 한 줄 |
| 반영 안 함 | `반영하지 않겠습니다.` + 이유와 근거 (`파일:줄`, AGENTS.md·계약 문서 이름) |
| 범위 밖 | `이 PR 범위 밖이라 반영하지 않겠습니다.` + 후속 이슈 번호 (만들었으면) |

- 비밀값·내부 IP·포트·서버 주소는 답변에도 쓰지 않는다 (공개 저장소)
- 사용자 판단 스레드는 사용자가 정할 때까지 답하지 않는다

## 6. resolve

답변을 단 스레드만 resolve한다. 답 없이 닫으면 리뷰어가 왜 닫혔는지 알 수 없다.

```bash
gh api graphql -f id=<스레드 id> -f query='
mutation($id: ID!) {
  resolveReviewThread(input: { threadId: $id }) { thread { isResolved } }
}' --jq '.data.resolveReviewThread.thread.isResolved'
```

- `true`가 아니면 실패한 스레드를 보고에 남긴다
- 사용자 판단으로 남은 스레드, 답변이 실패한 스레드는 열어 둔다

## 7. 보고

| # | 위치 | 판정 | 커밋 | 답변·resolve |
|---|---|---|---|---|
| 1 | `VmService.kt:42` | 반영 | `abc1234` | 완료 |

- 열어 둔 스레드와 이유, 사용자가 정해야 할 것을 따로 적는다
- 돌린 확인은 실패한 것만 원인과 함께 적는다 (AGENTS.md 4.1)
- 리뷰 반영 뒤 병합은 사람이 한다고 덧붙인다
