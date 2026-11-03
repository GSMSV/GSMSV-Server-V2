// 실행: node --test .claude/hooks/test/hooks.test.mjs
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { describe, it } from 'node:test'
import { findMerge } from '../block-pr-merge.mjs'
import { findProblems } from '../check-git-format.mjs'

const ok = (cmd, dialect = 'bash') => assert.deepEqual(findProblems(cmd, dialect), [], cmd)
const bad = (cmd, dialect = 'bash') => assert.notDeepEqual(findProblems(cmd, dialect), [], cmd)

describe('PR 병합 차단', () => {
  const blocked = [
    'gh pr merge 3',
    'gh pr merge 3 --squash --auto',
    'gh -R GSMSV/GSMSV-Server-V2 pr merge 3',
    'git push && gh pr merge',
    'g"h" pr "merge" 3',
    'gh.exe pr merge 3',
    'gh api -X PUT repos/GSMSV/GSMSV-Server-V2/pulls/3/merge',
    "gh api graphql -f query='mutation { mergePullRequest(input: {pullRequestId: \"x\"}) { clientMutationId } }'",
    "gh api graphql -f query='mutation { enablePullRequestAutoMerge(input: {}) { clientMutationId } }'",
    'curl -X PUT https://api.github.com/repos/o/r/pulls/3/merge',
  ]
  for (const cmd of blocked) it(`막음: ${cmd}`, () => assert.ok(findMerge(cmd).length > 0))

  const allowed = [
    'git merge origin/develop',
    'gh pr create --title "chore: 설정 추가" --body x',
    'gh pr view 3',
    'gh pr checks 3',
    'gh api repos/GSMSV/GSMSV-Server-V2/pulls/3',
    'gh pr list --search "is:merged"',
  ]
  for (const cmd of allowed) it(`허용: ${cmd}`, () => assert.deepEqual(findMerge(cmd), []))
})

describe('브랜치 이름', () => {
  it('맞는 이름', () => {
    ok('git switch -c feat/12-vm-create-api')
    ok('git checkout -b fix/31-login-redirect')
    ok('git branch -m chore/3-claude-code-harness')
    ok('git branch -m old chore/3-claude-code-harness')
    ok('git -C some/path switch -c docs/7-readme')
    ok('git worktree add -b test/9-grn-parser ../wt')
  })
  it('틀린 이름', () => {
    bad('git switch -c feature/12-vm')
    bad('git switch -c feat/vm-create-api') // 이슈 번호 없음
    bad('git checkout -b feat/12-VM_Create')
    bad('git branch feat/new-thing')
    bad('git branch -m chore/claude-code')
  })
  it('새 브랜치를 만들지 않는 명령은 검사하지 않는다', () => {
    ok('git switch develop')
    ok('git checkout develop')
    ok('git branch -D feat/whatever')
    ok('git branch --show-current')
    ok('git branch -a')
  })
})

describe('커밋 메시지', () => {
  it('맞는 메시지', () => {
    ok('git commit -m "feat: VM 생성 API 추가"')
    ok('git commit -m "feat(vm): VM 생성 API 추가"')
    ok('git commit -m "feat(vm)!: VM 응답 형식 변경"')
    ok('git commit -am "fix: 토큰 만료 처리 (#3)"')
    ok('git add . && git commit -m "chore: 설정 추가" -m "본문"')
    ok(`git commit -m "$(cat <<'EOF'\nchore: 하네스 설정 추가 (#3)\n\n본문 줄 "따옴표" 포함\nEOF\n)"`)
    ok(`git commit -F - <<'EOF'\ndocs: 규칙 문서 추가\n\n본문\nEOF`)
    ok('git commit --amend --no-edit')
  })
  it('틀린 메시지', () => {
    bad('git commit -m "Add VM API"')
    bad('git commit -m "feat:VM 생성"')
    bad('git commit -m "feat : VM 생성"')
    bad('git commit -m "feature: VM 생성"')
    bad('git commit -m "feat: VM 생성 API 추가."')
    bad('git commit -m "feat: add vm api"')
    bad('git commit -m "feat(VM): VM 생성"')
    bad(`git commit -m "$(cat <<'EOF'\nupdate stuff\nEOF\n)"`)
  })
  it('PowerShell here-string', () => {
    ok("git commit -m @'\nchore: 설정 추가\n\n본문 $literal\n'@", 'powershell')
    bad("git commit -m @'\nupdate\n'@", 'powershell')
  })
})

describe('PR·이슈 제목', () => {
  it('PR 제목', () => {
    ok('gh pr create --title "chore: 깃 컨벤션 및 Claude Code 하네스 설정 추가" --body x')
    ok('gh pr create -t "feat(vm)!: VM 응답 형식 변경" -b x')
    ok('gh pr edit 3 --title="docs: README 수정"')
    bad('gh pr create --title "Claude harness" --body x')
    bad('gh -R o/r pr create --title "feat: add" --body x')
  })
  it('이슈 제목에는 !를 쓰지 않는다', () => {
    ok('gh issue create --title "feat(vm): VM 생성 API" --body x')
    bad('gh issue create --title "feat(vm)!: VM 생성 API" --body x')
  })
})

describe('hook 실행', () => {
  const run = (script, input) =>
    spawnSync(process.execPath, [fileURLToPath(new URL(`../${script}`, import.meta.url))], {
      input: JSON.stringify(input),
      encoding: 'utf8',
    })

  it('병합 명령은 exit 2', () => {
    const r = run('block-pr-merge.mjs', { tool_name: 'Bash', tool_input: { command: 'gh pr merge 3' } })
    assert.equal(r.status, 2)
    assert.match(r.stderr, /PR 병합 금지/)
  })
  it('형식 위반은 exit 2', () => {
    const r = run('check-git-format.mjs', { tool_name: 'Bash', tool_input: { command: 'git commit -m "wip"' } })
    assert.equal(r.status, 2)
    assert.match(r.stderr, /커밋 메시지 제목/)
  })
  it('정상 명령은 exit 0', () => {
    const r = run('check-git-format.mjs', { tool_name: 'PowerShell', tool_input: { command: 'git status' } })
    assert.equal(r.status, 0)
  })
})
