#!/usr/bin/env node
// PreToolUse hook: AI 에이전트가 PR을 병합하지 못하게 막는다. 병합은 사람이 한다 (AGENTS.md).
// settings.json의 deny 규칙은 명령 앞부분만 비교해서 `gh -R x pr merge`, 따옴표 끼워 넣기 등으로
// 우회될 수 있다. 그래서 여기서는 명령 전체를 정규화한 뒤 다시 검사한다.
// 병합이 아닌 명령(git merge origin/develop 등)은 막지 않는다.

import { pathToFileURL } from 'node:url'

/** 따옴표·이스케이프를 지우고 소문자로 바꿔 `g"h" pr merge` 같은 우회를 없앤다 */
function normalize(command) {
  return command
    .toLowerCase()
    .replace(/["'`\\]/g, '')
    .replace(/[ \t]+/g, ' ')
}

const RULES = [
  {
    // gh pr merge (전역 옵션·--auto 포함). 명령 구분자(; & | 줄바꿈)를 넘지 않는다
    pattern: /\bgh(?:\.exe)?\b[^;&|\n]*?\bpr merge\b/,
    reason: '`gh pr merge`',
  },
  {
    // REST 병합 API: PUT /repos/{owner}/{repo}/pulls/{n}/merge
    pattern: /\/pulls\/[^/\s]+\/merge\b/,
    reason: 'GitHub 병합 REST API (`/pulls/{번호}/merge`)',
  },
  {
    // GraphQL 병합·자동 병합 mutation
    pattern: /\b(?:mergepullrequest|enablepullrequestautomerge|enqueuepullrequest)\b/,
    reason: 'GitHub 병합 GraphQL mutation',
  },
]

export function findMerge(command) {
  const text = normalize(command)
  return RULES.filter((r) => r.pattern.test(text)).map((r) => r.reason)
}

async function main() {
  let raw = ''
  for await (const chunk of process.stdin) raw += chunk
  let input
  try {
    input = JSON.parse(raw)
  } catch {
    process.exit(0)
  }
  const command = input?.tool_input?.command
  if (typeof command !== 'string') process.exit(0)
  const reasons = findMerge(command)
  if (reasons.length > 0) {
    process.stderr.write(
      `PR 병합 금지: AI 에이전트는 어떤 경우에도 PR을 병합하지 않는다. 병합은 사람이 한다 (AGENTS.md).\n` +
        `감지: ${reasons.join(', ')}\n` +
        `PR을 열고 사용자에게 병합을 요청한다.\n`,
    )
    process.exit(2)
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main()
