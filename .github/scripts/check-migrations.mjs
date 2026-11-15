// Flyway 마이그레이션 파일을 검사한다 (이름 형식, 버전 중복, 병합된 파일 수정).
// Flyway가 실제로 뜨는 테스트는 @Tag("integration")으로 빠져 있어서, 이 검사가 없으면 번호 중복이 배포 때까지 드러나지 않는다.
// Docker 없이 파일 이름과 git diff만 보므로 몇 초면 끝난다.
import { execFileSync } from 'node:child_process'

const DIR = 'src/main/resources/db/migration'
const BASE_REF = `refs/remotes/origin/${process.env.BASE_REF || 'develop'}`

// V{버전}__{설명}.sql. 설명은 영어 소문자 snake_case다
const VERSIONED = /^V([0-9]+(?:[._][0-9]+)*)__([a-z0-9]+(?:_[a-z0-9]+)*)\.sql$/
// 되풀이 마이그레이션은 버전이 없어 충돌하지 않는다. 이름만 본다
const REPEATABLE = /^R__[a-z0-9]+(?:_[a-z0-9]+)*\.sql$/
// 14자리 UTC 타임스탬프 (yyyyMMddHHmmss)
const TIMESTAMP = /^20[2-9][0-9](0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])([01][0-9]|2[0-3])[0-5][0-9][0-5][0-9]$/

const git = (...args) => execFileSync('git', args, { encoding: 'utf8' })

// Flyway는 버전의 밑줄을 점과 같게 보고 세그먼트를 숫자로 비교한다. V01과 V1은 같은 버전이다
const normalizeVersion = (version) =>
  version
    .split(/[._]/)
    .map(Number)
    .join('.')

const lines = (output) =>
  output
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)

const problems = []
const fail = (file, message) => problems.push({ file, message })

// base 브랜치에 이미 있는 마이그레이션. 여기 있는 버전은 다시 쓸 수 없다
const baseVersions = new Map()
for (const path of lines(git('ls-tree', '--name-only', BASE_REF, '--', `${DIR}/`))) {
  const matched = VERSIONED.exec(path.slice(DIR.length + 1))
  if (matched) baseVersions.set(normalizeVersion(matched[1]), path)
}

// 이름이 바뀌면 "지우고 새로 추가"로 보고 싶어서 --no-renames를 쓴다. 병합된 파일의 이름 변경도 막아야 한다
const changes = lines(git('diff', '--no-renames', '--name-status', `${BASE_REF}...HEAD`, '--', DIR)).map((line) => {
  const [status, path] = line.split('\t')
  return { status: status[0], path }
})

const addedVersions = new Map()

for (const { status, path } of changes) {
  const name = path.slice(DIR.length + 1)

  if (status === 'M' || status === 'D') {
    fail(
      path,
      '이미 병합된 마이그레이션은 고치거나 지우거나 이름을 바꾸지 않는다. 체크섬이 달라져 기동이 깨진다. 고칠 게 있으면 새 버전을 추가한다 (AGENTS.md 2.3)',
    )
    continue
  }
  if (status !== 'A') continue

  if (REPEATABLE.test(name)) continue

  const matched = VERSIONED.exec(name)
  if (!matched) {
    fail(path, '파일 이름은 `V{UTC 타임스탬프}__{설명}.sql`이어야 한다 (밑줄 2개, 설명은 영어 소문자 snake_case)')
    continue
  }

  const [, version] = matched
  if (!TIMESTAMP.test(version)) {
    fail(
      path,
      `버전 \`${version}\`은 14자리 UTC 타임스탬프가 아니다. 순서대로 번호를 올리지 않는다 (병렬 브랜치가 같은 번호를 잡는다). \`date -u +V%Y%m%d%H%M%S\`로 만든다`,
    )
    continue
  }

  const normalized = normalizeVersion(version)
  const conflict = baseVersions.get(normalized) ?? addedVersions.get(normalized)
  if (conflict) {
    fail(path, `버전 ${normalized}이 \`${conflict}\`와 겹친다. Flyway가 중복 버전으로 기동을 거부한다`)
    continue
  }
  addedVersions.set(normalized, path)
}

if (problems.length === 0) {
  console.log(`마이그레이션 검사 통과 (${DIR}에서 바뀐 파일 ${changes.length}개)`)
  process.exit(0)
}

for (const { file, message } of problems) {
  console.log(`::error file=${file},title=마이그레이션 규칙 위반::${file}: ${message}`)
}
console.log(`마이그레이션 검사 실패: ${problems.length}건`)
process.exitCode = 1
