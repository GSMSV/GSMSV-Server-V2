// 브랜치·커밋·PR·이슈 제목 규칙. 원본은 .github/CONTRIBUTING.md 4장, 요약은 docs/rules/git.md

export const TYPES = ['feat', 'fix', 'docs', 'style', 'refactor', 'perf', 'test', 'build', 'ci', 'chore', 'revert']

const TYPE_GROUP = `(${TYPES.join('|')})`
const BRANCH_RE = new RegExp(`^${TYPE_GROUP}/(\\d+)-[a-z0-9]+(?:-[a-z0-9]+)*$`)
const TITLE_RE = new RegExp(`^${TYPE_GROUP}(\\(([^)]*)\\))?(!)?: (.*)$`)
const SCOPE_RE = /^[a-z][a-z0-9]*$/
const HANGUL_RE = /[가-힣]/

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
 * @returns {string[]}
 */
export function checkTitle(title, kind) {
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
    problems.push(`scope \`${scope}\`: 도메인 패키지 이름을 그대로 쓴다 (영어 소문자, 예: vm)`)
  }
  if (bang && kind === 'issue') problems.push('이슈 제목에는 !를 쓰지 않는다')
  if (description.length === 0 || /^\s/.test(description)) problems.push('콜론 뒤에는 정확히 한 칸 띄운다')
  if (/\s$/.test(description)) problems.push('끝에 공백을 두지 않는다')
  if (/\.$/.test(description)) problems.push('끝에 마침표를 찍지 않는다')
  if (!HANGUL_RE.test(description)) problems.push('설명은 한국어로 쓴다')
  return problems.length ? [header, ...problems] : []
}
