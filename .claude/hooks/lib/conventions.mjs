// 브랜치·커밋·PR·이슈 제목 규칙. 원본은 .github/CONTRIBUTING.md 4장, AI용 요약은 AGENTS.md 1장

export const TYPES = ['feat', 'fix', 'docs', 'style', 'refactor', 'perf', 'test', 'build', 'ci', 'chore', 'revert']

const TYPE_GROUP = `(${TYPES.join('|')})`
const BRANCH_RE = new RegExp(`^${TYPE_GROUP}/(\\d+)-[a-z0-9]+(?:-[a-z0-9]+)*$`)
const TITLE_RE = new RegExp(`^${TYPE_GROUP}(\\(([^)]*)\\))?(!)?: (.*)$`)
const SCOPE_RE = /^[a-z][a-z0-9]*$/
const HANGUL_RE = /[가-힣]/
// develop → main 릴리스 PR 제목. 같은 날 두 번째 릴리스부터 순번을 1, 2…로 올린다
const RELEASE_TITLE_RE = /^v\d{8}\.(0|[1-9]\d*)$/
export const RELEASE_BASE = 'main'

const TITLE_EXAMPLE = '예: feat: VM 생성 API 추가, feat(vm)!: VM 응답 형식 변경'

/** @returns {string[]} 문제 목록. 비어 있으면 통과 */
export function checkBranch(name) {
  if (BRANCH_RE.test(name)) return []
  const problems = []
  const [type, rest = ''] = name.split(/\/(.*)/s)
  if (!TYPES.includes(type)) problems.push(`타입 \`${type}\`은 쓸 수 없다. 가능한 타입: ${TYPES.join(' ')}`)
  if (!/^\d+-/.test(rest)) {
    problems.push('이슈 번호가 없다. AI가 만드는 브랜치는 이슈 번호가 필수다. 이슈가 없으면 사용자에게 이슈를 먼저 만들자고 제안한다')
  } else if (!/^\d+-[a-z0-9]+(?:-[a-z0-9]+)*$/.test(rest)) {
    problems.push('설명은 영어 소문자·숫자이고 단어는 하이픈(-)으로 잇는다')
  }
  if (problems.length === 0) problems.push('형식이 맞지 않는다')
  return [`브랜치 이름 \`${name}\`: 형식은 \`타입/이슈번호-설명\` (예: feat/12-vm-create-api)`, ...problems]
}

/**
 * @param {string} title
 * @param {'commit' | 'pr' | 'issue'} kind
 * @param {{ base?: string }} [options] PR의 base 브랜치. `main`이면 릴리스 제목 형식만 받는다
 * @returns {string[]}
 */
export function checkTitle(title, kind, { base } = {}) {
  if (kind === 'pr' && base === RELEASE_BASE) return checkReleaseTitle(title)
  const label = { commit: '커밋 메시지 제목', pr: 'PR 제목', issue: '이슈 제목' }[kind]
  const format = kind === 'issue' ? '`타입(scope): 한국어 설명`' : '`타입(scope)!: 한국어 설명`'
  const header = `${label} \`${title}\`: 형식은 ${format}. scope와 !는 선택 (${TITLE_EXAMPLE})`

  const m = TITLE_RE.exec(title)
  if (!m) {
    const problems = []
    const typeMatch = /^([A-Za-z]+)/.exec(title)
    if (!typeMatch || !TYPES.includes(typeMatch[1])) {
      problems.push(`타입은 소문자로 다음 중 하나: ${TYPES.join(' ')}`)
    } else if (/^[a-z]+(\([^)]*\))?!?\s+:/.test(title)) {
      problems.push('콜론 앞에는 띄우지 않는다')
    } else {
      problems.push('콜론(:) 뒤에 한 칸 띄운다')
    }
    return [header, ...problems]
  }

  const [, , scopeGroup, scope, bang, description] = m
  const problems = []
  if (scopeGroup !== undefined && !SCOPE_RE.test(scope)) {
    problems.push(`scope \`${scope}\`: 도메인 패키지나 global 하위 패키지 이름을 그대로 쓴다 (영어 소문자, 예: vm, config)`)
  }
  if (bang && kind === 'issue') problems.push('이슈 제목에는 !를 쓰지 않는다')
  if (description.length === 0 || /^\s/.test(description)) problems.push('콜론 뒤에는 정확히 한 칸 띄운다')
  if (/\s$/.test(description)) problems.push('끝에 공백을 두지 않는다')
  if (/\.$/.test(description)) problems.push('끝에 마침표를 찍지 않는다')
  if (!HANGUL_RE.test(description)) problems.push('설명은 한국어로 쓴다')
  return problems.length ? [header, ...problems] : []
}

/** develop → main 릴리스 PR 제목: `vYYYYMMDD.N` (예: v20261117.0) */
export function checkReleaseTitle(title) {
  if (RELEASE_TITLE_RE.test(title)) return []
  return [
    `main 대상 PR 제목 \`${title}\`: 형식은 \`vYYYYMMDD.N\` (예: v20261117.0)`,
    '날짜는 PR을 만드는 날(한국 시간), N은 그날의 릴리스 순번으로 0부터 시작한다. 같은 날 main 대상 PR이 이미 있으면 1, 2…로 올린다',
  ]
}
