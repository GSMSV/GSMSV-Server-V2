# AGENTS.md

GSMSV v2의 Kotlin API 서버(Spring Boot)다. AI 코딩 도구(Claude Code, Codex)는 이 문서를 따른다. 이 저장소의 AI용 규칙은 이 문서 한 곳에만 둔다.

- 사람용 원본 규칙은 [.github/CONTRIBUTING.md](.github/CONTRIBUTING.md)다. 깃 규칙이 충돌하면 CONTRIBUTING이 우선한다
- 저장소끼리 함께 쓰는 약속(API 명세, 권한, 자원 이름)의 원본은 [GSMSV-Contracts-V2](https://github.com/GSMSV/GSMSV-Contracts-V2)(이하 "계약")다. 이 문서는 계약을 다시 쓰지 않고 가리키기만 한다. 이 문서와 계약이 다르면 계약이 맞다
- 커밋·PR·이슈 절차는 `.claude/skills/`의 스킬에 있다 (`git-commit`, `write-pr`, `create-issue`). Codex는 해당 `SKILL.md`를 읽고 그 절차를 따른다

## 절대 규칙

- **사용자에게 보내는 답변, 커밋·PR·이슈, 주석, 문서는 한국어로 쓴다.** 코드 식별자와 명령어는 그대로 둔다
- **AI 에이전트는 어떤 경우에도 PR을 병합하지 않는다. 병합은 사람이 한다.**
  `gh pr merge`(`--auto` 포함), `gh api`로 병합 API를 부르는 것 모두 금지다. PR을 열고 사용자에게 병합을 요청한다
- **공개 저장소다.** 비밀값(토큰·비밀번호·키)·내부 IP·포트·서버 주소를 코드·커밋·이슈·PR·댓글에 쓰지 않는다 ([5.1](#51-공개-저장소))
- 의존성 추가나 버전 변경은 사용자 확인 후에만 한다
- 브랜치를 만들 때 이슈 번호는 필수다. 이슈가 없으면 먼저 이슈를 만들자고 제안한다

## 자주 쓰는 명령

```bash
./gradlew compileKotlin compileTestKotlin    # 컴파일 확인
./gradlew ktlintCheck                        # 코드 스타일 검사 (Kotlin 파일 커밋 전 필수)
./gradlew ktlintFormat                       # 스타일 자동 수정
./gradlew test --tests 'com.gsmsv.api.vm.*'  # 관련 테스트만 (4장)
node --test .claude/hooks/test/hooks.test.mjs  # .claude/hooks/를 고쳤을 때
```

- 전체 테스트(`./gradlew test`)는 로컬에서 돌리지 않는다. CI가 한다
- `ktlintCheck`가 실패하면 `ktlintFormat`으로 고친 뒤 다시 검사한다. `ktlintFormat`이 못 고치는 위반은 직접 고친다. 규칙을 끄거나 `@Suppress`로 피하지 않는다
- git pre-commit hook은 두지 않는다. 원격 검사는 CI가 한다

## 1. 깃

원본: [CONTRIBUTING 3~5장](.github/CONTRIBUTING.md). Claude Code에서는 hook(`.claude/hooks/check-git-format.mjs`)이 형식을 검사한다. 규칙 값은 `.claude/hooks/lib/conventions.mjs`에 있다.

### 1.1 타입

`feat fix docs style refactor perf test build ci chore revert` (11종, 언제 쓰는지는 CONTRIBUTING 4장)

### 1.2 형식

| 대상 | 형식 | 예 |
|---|---|---|
| 브랜치 | `타입/이슈번호-설명` | `feat/12-vm-create-api` |
| 커밋·PR 제목 | `타입(scope)!: 한국어 설명` | `feat(vm): VM 생성 API 추가` |
| 이슈 제목 | `타입(scope): 한국어 설명` | `feat(vm): VM 생성 API` |

- 브랜치: 정규식 `^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)/[0-9]+-[a-z0-9]+(-[a-z0-9]+)*$`. 설명은 영어 소문자·숫자, 단어는 하이픈(`-`)으로 잇는다
- `(scope)`는 선택이다. 영어 소문자(`^[a-z][a-z0-9]*$`)이고, 값은 아래 둘 중 하나의 패키지 이름을 그대로 쓴다. 목록은 따로 관리하지 않는다. 여러 영역에 걸치거나 해당 영역이 없으면 뺀다
  - 도메인 패키지: `vm`, `team`, `user` …
  - `global` 하위 패키지: `config`, `response`, `exception`, `grn`, `authorization` …
- `!`는 호환성을 깨는 변경(API 계약 변경)에만 붙인다. 이슈 제목에는 쓰지 않는다
- 콜론 앞은 붙이고 뒤는 한 칸 띄운다. 설명은 한국어, 끝에 마침표 없음
- 커밋 타입은 커밋 내용에 맞춰 고른다 (브랜치 타입과 달라도 된다. 예: `feat` 브랜치 안의 `test` 커밋). PR 제목 타입은 브랜치 타입을 쓴다
- 한 PR에는 한 가지 일만. **PR 제목이 그대로 develop의 squash 커밋 메시지가 된다**

예: `feat(vm): VM 생성 API 추가`, `test(grn): GRN 파서 실패 케이스 추가`, `build: ktlint 도입`, `feat(vm)!: VM 응답 형식 변경`

### 1.3 라벨

이슈와 PR 모두 타입에 맞춰 `--label`로 붙인다.

| 타입 | 라벨 |
|---|---|
| `feat` | `enhancement` |
| `fix` | `bug` |
| `docs` | `documentation` |
| 나머지 | `chore` |

### 1.4 이슈·커밋·PR

- 이슈는 `create-issue` 스킬로 만든다
- 커밋은 `git-commit` 스킬, PR은 `write-pr` 스킬로 만든다
- PR의 base는 `develop`이다. develop을 따라잡을 때는 `git fetch origin && git merge origin/develop` (rebase·force push는 하지 않는다)

## 2. Kotlin 코드

### 2.1 언어

- ktlint(`ktlint_official` 스타일)를 따른다. 설정은 `.editorconfig`에 있다
- `!!` 금지 (테스트 제외). `lateinit`은 테스트와 Spring 주입에만 쓴다
- `val`과 읽기 전용 컬렉션(`List`, `Map`)을 먼저 쓴다
- 주석은 한국어로 "왜"만 적는다. KDoc은 공개 API에만 쓴다

### 2.2 Spring

- 생성자 주입만 쓴다 (필드 주입·setter 주입 금지)
- 요청·응답은 `data class` DTO로 만들고 이름은 `XxxRequest`/`XxxResponse`. 입력은 Bean Validation으로 검증한다
- `@Transactional`은 service에만 붙인다. 조회 메서드는 `@Transactional(readOnly = true)`
- 예외는 `ErrorCode` enum + `BusinessException` + `@RestControllerAdvice`로 처리한다
- 오류·응답 형식은 계약의 명세를 따른다
- 로깅은 kotlin-logging. 토큰·비밀번호·내부 IP는 로그에 남기지 않는다
- 설정 파일은 `application.yml`. 비밀값은 환경변수로만 받는다 (`${DB_PASSWORD}` 형태, 기본값에 실제 값 금지)

### 2.3 JPA·Flyway

- 엔티티는 `data class` 금지
- 상태는 엔티티 메서드로만 바꾼다 (외부에서 필드에 직접 대입하지 않는다)
- 연관관계는 기본 `FetchType.LAZY`
- 마이그레이션 파일 이름은 `V{번호}__{설명}.sql` (밑줄 2개)
- develop에 병합된 마이그레이션은 수정하지 않는다. 고칠 게 있으면 새 버전을 추가한다

## 3. 구조

### 3.1 패키지

```text
com.gsmsv.api
├── {도메인}/            예: vm, team, user
│   ├── controller
│   ├── service
│   ├── repository
│   ├── domain          엔티티
│   └── dto
├── global/
│   ├── config
│   ├── security
│   ├── exception
│   ├── response
│   ├── util
│   ├── grn             GRN 파서 (5.4)
│   └── authorization   자원 로더, 규칙표, listVisible (5.3)
└── infra/              Infra-V2 연동 클라이언트
```

### 3.2 의존 방향

- controller → service → repository. 반대 방향은 없다
- 다른 도메인의 repository를 직접 부르지 않는다. 그 도메인의 service를 통해 호출한다
- 엔티티는 controller 밖으로 내보내지 않는다. 응답은 DTO로 바꿔서 돌려준다
- Infra-V2 호출은 `infra` 패키지의 클라이언트로만 한다. 각 도메인 service가 부르고, 권한 로더를 통과한 뒤에만 부른다
- Kotlin과 Infra-V2(Go)의 역할 경계는 계약 `policies/kotlin-go-boundary.md`(34번)를 따른다

### 3.3 API는 계약 먼저

- 계약 `rules/api-rules.md` 1장(계약 먼저)을 따른다. API를 새로 만들거나 바꿀 때는 계약의 OpenAPI 명세가 먼저다
- **명세가 없는 API는 구현하지 않는다.** 명세가 없으면 사용자에게 Contracts-V2 PR을 먼저 올리자고 제안한다

## 4. 테스트

### 4.1 로컬에서 돌리는 범위

로컬에서는 관련 테스트만 돌린다.

1. 바뀐 클래스의 테스트
2. 같은 패키지의 테스트
3. 바뀐 클래스를 직접 쓰는 클래스의 테스트

```bash
./gradlew test --tests 'com.gsmsv.api.vm.service.VmServiceTest'
./gradlew test --tests 'com.gsmsv.api.vm.*'
```

- Testcontainers를 쓰는 테스트(DB 등)는 `@Tag("integration")`을 붙인다. DB 스키마·repository·설정처럼 관련 부분이 바뀔 때만 로컬에서 돌린다
- Gradle 빌드 캐시가 켜져 있다 (`gradle.properties`의 `org.gradle.caching=true`)
- 결과는 **실패한 테스트와 원인만** 보고한다. 통과한 목록이나 전체 로그를 붙이지 않는다

### 4.2 스타일

- JUnit 5 + MockK(springmockk). Mockito는 쓰지 않는다
- 테스트 이름은 백틱 한국어: ``fun `만료된 토큰이면 401을 돌려준다`()``
- 테스트 코드에서는 `!!`, `lateinit`을 써도 된다
- GRN·id 파서 테스트는 계약 `constants/resource-id.md` 6장의 맞는/틀린 id 목록을 그대로 쓴다

## 5. 보안

### 5.1 공개 저장소

- 비밀값을 실수로 올렸으면 커밋만 지우고 넘어가지 않는다. 바로 사용자에게 알린다 (교체가 필요하다)
- 보안 문제는 공개 이슈로 쓰지 않는다 ([SECURITY.md](.github/SECURITY.md))

### 5.2 계약 원본

규칙 내용(규칙표, GRN 형식, 접두사, 정규식, 404·403 기준)은 계약에만 있다. 권한이나 자원 접근 코드를 건드리기 전에 해당 문서를 읽는다. 아래 5.3·5.4에는 이 서버 코드에만 걸리는 규칙만 둔다.

| 규칙 | 계약 문서 |
|---|---|
| 21번 팀과 역할 (규칙표) | `rules/team-ownership.md`, `rules/authorization.md` 8장 |
| 22번 권한 판단 Layer | `rules/authorization.md` |
| 23번 자원 이름 규칙 (GRN 형식) | `rules/resource-naming.md` |
| 접두사·글자·길이·정규식 | `constants/resource-id.md` |

**(미정)** 은 계약이 아직 정하지 않은 항목이다. 지금 서버가 어떻게 동작하는지만 적는다. 계약이 정해지면 서버 코드와 이 문서를 함께 고친다.

### 5.3 권한 판단 (22번)

어기면 보안 사고로 이어진다.

- 권한 판단은 이 서버의 **로더에서만** 한다 (`com.gsmsv.api.global.authorization`). Infra-V2에는 권한 판단 코드를 두지 않는다. Infra 호출은 로더를 통과한 뒤에만 한다
- 로더는 3종이다
  - `ServiceResourceLoader` (GRN): 파서 검사 → id로 DB 1회 조회 → DB 값으로 GRN을 다시 만들어 요청 GRN과 통째로 비교(다르면 404) → 규칙표 판단
  - `SubResourceLoader` (부모 GRN + 하위 id, 또는 하위 자원 표기): 하위 id 형식 검사 → 부모를 위 과정으로 통과시킨 뒤 하위 자원을 찾고, 실제 부모가 요청의 부모와 같은지 확인(다르면 404). 부모 GRN 없이 하위 id만으로 찾는 길은 두지 않는다
  - `TeamLoader` (팀 id): 팀 id 형식 검사 → 같은 규칙표로 판단하고 감사 로그를 남긴다. 팀 앞으로 VM·데이터베이스를 만드는 동작도 이 로더로 판단한다
- 목록 조회는 같은 규칙표를 쓰는 `listVisible(user)`만 쓴다
- 서비스 자원 repository의 id 조회는 로더와 `listVisible` 밖에서 호출하지 않는다
- service는 id가 아니라 **로더가 돌려준 자원 객체**를 받는다
- controller·service에서 소유자·역할을 직접 비교하지 않는다. 팀 멤버십은 캐시하지 않는다
- 코드에서는 `Grant`가 규칙표의 범위이고, `Action`마다 `Grant` 하나를 붙인다. "본인"이 필요한 동작은 `TeamLoader`에 대상 사용자 id를 넘긴다
- 승인되지 않은 팀의 VM·데이터베이스 생성은 `TeamLoader`가 권한 판단 뒤에 검사하고 403(`TEAM_NOT_APPROVED`)을 돌려준다. 관리자도 같다
- **(미정)** 403 `details`의 키 이름. 지금 서버는 `details.required_role`, `details.actor_role`로 둔다 (계약 `rules/api-rules.md` 2.1, 이슈 없음)
- **(미정)** `SYSTEM`의 동작 범위. 지금 서버는 `SYSTEM`에 모든 동작을 허용한다 (계약 `rules/authorization.md` 8장, 이슈 없음)
- **(미정)** 개인 자원 소유자의 권한 범위. 지금 서버는 관리자 전용 동작을 뺀 모든 동작을 허용한다 (계약 `rules/authorization.md` 13장, 이슈 없음)
- **(미정)** 동작 이름 형식(`vm.delete`처럼 자원 접두사를 붙이는지)과 목록. 지금 `Action`은 자원 종류가 없는 enum이다 (계약 `rules/api-rules.md` 3장, 이슈 없음)

### 5.4 GRN·id 파서 (23번)

- 파서는 이 저장소(`com.gsmsv.api.global.grn`)에 구현한다. 계약에는 정의만 둔다
  - `GrnParser`: 서비스 자원 GRN과 하위 자원 표기
  - `IdParser`: 사용자·팀 id, 하위 자원 id
  - 하위 자원이 붙을 수 있는 부모 종류는 `SubResourceType`에 있다
- 파서는 정규식으로 문자열 전체를 검사하고 **자원 id만** 돌려준다. 소유자 칸 값은 파서 밖으로 꺼내지 않고, 권한 판단에 쓰지 않는다
- **GRN 문자열을 직접 자르지 않는다** (`split`, `substring` 등 금지). 항상 파서를 쓴다
- 사용자·팀·하위 자원 id도 DB를 조회하기 전에 형식을 검사한다
- **(미정)** 형식 오류의 응답 코드. 지금 서버는 400(`INVALID_GRN`, `INVALID_RESOURCE_ID`)으로 둔다. 형식 오류는 존재 여부를 드러내지 않는다 (GSMSV/GSMSV-Contracts-V2#4)
- **(미정)** 형식은 맞지만 엔드포인트와 자원 종류가 다를 때(예: VM API에 `fn-` GRN)의 응답. 지금 서버는 400(`GRN_RESOURCE_TYPE_MISMATCH`)으로 둔다 (GSMSV/GSMSV-Contracts-V2#4)
- **(미정)** 하위 자원 표기의 정규식 표기. 지금 서버는 `^{서비스 자원 GRN}/{하위 접두사}-{무작위 8자}$`로 한 단계만 받는다 (GSMSV/GSMSV-Contracts-V2#5)
- **(미정)** 하위 자원이 그 부모 종류에 붙을 수 없을 때(예: 서버리스 함수 밑의 `snap-`) 정규식과 로더 중 어디서 거절할지. 지금 서버는 파서가 형식 오류로 거절한다 (GSMSV/GSMSV-Contracts-V2#5)
