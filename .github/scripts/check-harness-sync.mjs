// Claude Code 스킬(.claude/skills)과 Codex 스킬 사본(.agents/skills)이 같은지 검사한다.
// 한쪽만 고치면 두 도구가 다른 규칙으로 일하게 된다 (Codex 사본이 2주 넘게 갱신되지 않은 적이 있다).
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const SOURCE = '.claude/skills'
const COPY = '.agents/skills'

const listFiles = (root, dir = root) =>
  readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    return statSync(path).isDirectory() ? listFiles(root, path) : [relative(root, path).replaceAll('\\', '/')]
  })

// Windows에서 체크아웃하면 줄바꿈이 CRLF가 될 수 있어서 내용만 비교한다
const read = (path) => readFileSync(path, 'utf8').replaceAll('\r\n', '\n')

const source = new Set(listFiles(SOURCE))
const copy = new Set(listFiles(COPY))
const problems = []

for (const file of source) {
  if (!copy.has(file)) problems.push(`${COPY}/${file} 없음`)
  else if (read(join(SOURCE, file)) !== read(join(COPY, file))) problems.push(`${COPY}/${file} 내용이 다름`)
}
for (const file of copy) {
  if (!source.has(file)) problems.push(`${COPY}/${file}: ${SOURCE}에 없는 파일`)
}

if (problems.length > 0) {
  console.error(`스킬 사본이 원본과 다르다. ${SOURCE}를 고친 뒤 ${COPY}에 그대로 복사한다:`)
  for (const problem of problems) console.error(`- ${problem}`)
  process.exit(1)
}
console.log(`스킬 사본 일치 (${source.size}개 파일)`)
