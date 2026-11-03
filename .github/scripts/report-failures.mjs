// CI가 실패했을 때 ktlint 위반과 실패한 테스트를 PR에서 바로 보이게 한다.
// - 파일에 붙는 annotation(::error)으로 PR의 Files changed 탭에 표시
// - Job Summary에 표로 정리
import { appendFileSync, existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const workspace = process.env.GITHUB_WORKSPACE ?? process.cwd()
const summaryPath = process.env.GITHUB_STEP_SUMMARY

function listFiles(dir, ext) {
  if (!existsSync(dir)) return []
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return listFiles(path, ext)
    return path.endsWith(ext) ? [path] : []
  })
}

function escapeData(s) {
  return s.replace(/%/g, '%25').replace(/\r/g, '%0D').replace(/\n/g, '%0A')
}

function escapeProperty(s) {
  return escapeData(s).replace(/:/g, '%3A').replace(/,/g, '%2C')
}

function escapeCell(s) {
  return s.replace(/\|/g, '\\|').replace(/\r?\n/g, ' ')
}

function unescapeXml(s) {
  return s
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)))
    .replace(/&amp;/g, '&')
}

// ktlint plain 리포트: `경로:줄:칸: 메시지 (규칙)` (색 코드 포함)
function readKtlintViolations() {
  const violations = []
  for (const file of listFiles(join(workspace, 'build/reports/ktlint'), '.txt')) {
    const text = readFileSync(file, 'utf8').replace(/\x1b\[[0-9;]*m/g, '')
    for (const line of text.split(/\r?\n/)) {
      const m = /^(.+?\.kts?):(\d+):(\d+): (.*)$/.exec(line)
      if (!m) continue
      const [, path, row, col, message] = m
      violations.push({ path: relative(workspace, path).replace(/\\/g, '/'), row, col, message })
    }
  }
  return violations
}

// JUnit XML 리포트에서 failure·error가 있는 testcase를 찾는다
function readTestFailures() {
  const failures = []
  for (const file of listFiles(join(workspace, 'build/test-results'), '.xml')) {
    const xml = readFileSync(file, 'utf8')
    const caseRe = /<testcase\b([^>]*?)(?:\/>|>([\s\S]*?)<\/testcase>)/g
    for (const [, attrs, body = ''] of xml.matchAll(caseRe)) {
      const failure = /<(failure|error)\b([^>]*?)(?:\/>|>)/.exec(body)
      if (!failure) continue
      const attr = (src, name) => unescapeXml(new RegExp(`\\b${name}="([^"]*)"`).exec(src)?.[1] ?? '')
      const message = attr(failure[2], 'message') || attr(failure[2], 'type')
      failures.push({
        className: attr(attrs, 'classname'),
        name: attr(attrs, 'name'),
        message: message.split(/\r?\n/)[0],
      })
    }
  }
  return failures
}

const violations = readKtlintViolations()
const failures = readTestFailures()

for (const v of violations) {
  console.log(`::error file=${escapeProperty(v.path)},line=${v.row},col=${v.col},title=ktlint::${escapeData(v.message)}`)
}
for (const f of failures) {
  console.log(`::error title=${escapeProperty(`테스트 실패: ${f.className}`)}::${escapeData(`${f.name}: ${f.message}`)}`)
}

const lines = []
if (violations.length > 0) {
  lines.push(`### ktlint 위반 ${violations.length}건`, '', '`./gradlew ktlintFormat`으로 대부분 자동 수정된다.', '')
  lines.push('| 위치 | 내용 |', '|---|---|')
  for (const v of violations) lines.push(`| \`${v.path}:${v.row}:${v.col}\` | ${escapeCell(v.message)} |`)
  lines.push('')
}
if (failures.length > 0) {
  lines.push(`### 실패한 테스트 ${failures.length}건`, '')
  lines.push('| 테스트 | 메시지 |', '|---|---|')
  for (const f of failures) lines.push(`| \`${f.className}\` › ${escapeCell(f.name)} | ${escapeCell(f.message)} |`)
  lines.push('')
}
if (lines.length === 0) {
  lines.push('ktlint 위반이나 실패한 테스트를 찾지 못했다. 컴파일 오류일 수 있으니 로그를 확인한다.')
}
if (summaryPath) appendFileSync(summaryPath, `${lines.join('\n')}\n`)
else console.log(lines.join('\n'))
