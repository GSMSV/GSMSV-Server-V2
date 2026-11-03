# AGENTS.md

GSMSV v2의 Kotlin API 서버(Spring Boot)다. AI 코딩 도구(Claude Code, Codex)는 이 문서와 아래 규칙 문서를 따른다.
사람용 원본 규칙은 [.github/CONTRIBUTING.md](.github/CONTRIBUTING.md)이고, 충돌하면 CONTRIBUTING이 우선한다.

## 절대 규칙

- **사용자에게 보내는 답변, 커밋·PR·이슈, 주석, 문서는 한국어로 쓴다.** 코드 식별자와 명령어는 그대로 둔다

- **AI 에이전트는 어떤 경우에도 PR을 병합하지 않는다. 병합은 사람이 한다.**
  `gh pr merge`(`--auto` 포함), `gh api`로 병합 API를 부르는 것 모두 금지다. PR을 열고 사용자에게 병합을 요청한다
- **공개 저장소다.** 비밀값·내부 IP·포트·서버 주소를 코드·커밋·이슈·PR에 쓰지 않는다 ([security.md](docs/rules/security.md))
- 의존성 추가나 버전 변경은 사용자 확인 후에만 한다
- 브랜치를 만들 때 이슈 번호는 필수다. 이슈가 없으면 먼저 이슈를 만들자고 제안한다

## 규칙 문서

| 문서 | 내용 |
|---|---|
| [docs/rules/git.md](docs/rules/git.md) | 브랜치·커밋·PR·이슈 형식, 라벨 |
| [docs/rules/testing.md](docs/rules/testing.md) | 어떤 테스트를 돌릴지, 테스트 스타일 |
| [docs/rules/kotlin.md](docs/rules/kotlin.md) | Kotlin·Spring·JPA 코딩 규칙 |
| [docs/rules/architecture.md](docs/rules/architecture.md) | 패키지 구조, 의존 방향, API 명세 우선 |
| [docs/rules/security.md](docs/rules/security.md) | 공개 저장소 규칙, 권한 판단 Layer(22번), GRN 규칙(23번) |

작업 종류에 맞는 문서를 먼저 읽는다. 코드를 쓰기 전에는 `kotlin.md`·`architecture.md`, 자원 접근이나 권한 코드를 건드리면 `security.md`를 반드시 읽는다.

## 자주 쓰는 명령

```bash
./gradlew compileKotlin compileTestKotlin   # 컴파일 확인
./gradlew ktlintCheck                       # 코드 스타일 검사 (커밋 전 필수)
./gradlew ktlintFormat                      # 스타일 자동 수정
./gradlew test --tests 'com.gsmsv.api.vm.*' # 관련 테스트만 (testing.md)
```

전체 테스트(`./gradlew test`)는 로컬에서 돌리지 않는다. CI가 한다.
