// PR 제목이 `타입(scope)!: 한국어 설명` 형식인지 검사한다.
// 규칙은 Claude Code hook과 같은 .claude/hooks/lib/conventions.mjs를 그대로 쓴다.
import { checkTitle } from '../../.claude/hooks/lib/conventions.mjs'

const title = process.env.PR_TITLE ?? ''
const problems = checkTitle(title, 'pr')

if (problems.length === 0) {
  console.log(`PR 제목 형식 통과: ${title}`)
  process.exit(0)
}

const [header, ...details] = problems
console.log(`::error title=PR 제목 형식 오류::${[header, ...details.map((p) => `- ${p}`)].join('%0A')}`)
process.exitCode = 1
