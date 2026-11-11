# GSMSV-Server-V2

[ GSMSV-V2 | Server ] 광주소프트웨어마이스터고 재학생을 위한 교내 Infra Platform API Server

GSMSV v2의 API 서버(Kotlin, Spring Boot)다. 인증·권한·오류 형식·GRN 같은 공통 골격이 있고, 도메인 API는 이 골격 위에 만든다.

## GSMSV v2 저장소

| 저장소 | 내용 |
|---|---|
| [GSMSV-Server-V2](https://github.com/GSMSV/GSMSV-Server-V2) | API 서버 (Kotlin) |
| [GSMSV-Client-V2](https://github.com/GSMSV/GSMSV-Client-V2) | 웹 콘솔 (React) |
| [GSMSV-Infra-V2](https://github.com/GSMSV/GSMSV-Infra-V2) | Proxmox·k3s를 다루는 실행 엔진 (Go) |
| [GSMSV-Contracts-V2](https://github.com/GSMSV/GSMSV-Contracts-V2) | 저장소끼리 함께 쓰는 약속 (API 명세, 자원 이름 규칙) |

## 로컬 실행

JDK 21과 Docker가 필요하다. DB(PostgreSQL)는 `compose.yaml`로 앱이 뜰 때 자동으로 같이 뜬다.

```bash
export JWT_SECRET=$(openssl rand -base64 32)   # 토큰 서명 키. 실제 값을 저장소에 적지 않는다
export DATAGSM_CLIENT_ID=...                   # DataGSM OAuth 클라이언트 등록 정보. 비어 있으면 기동에 실패한다
export DATAGSM_CLIENT_SECRET=...
export DATAGSM_REDIRECT_URI=...                # DataGSM에 등록한 콜백 주소와 같아야 한다
./gradlew bootRun                              # 프로필을 주지 않으면 local
```

로그인 없이 토큰을 받으려면 개발용 토큰 API를 쓴다. `local`·`dev` 프로필에서만 열린다.

```bash
curl -X POST "$API/api/v2/dev/token" \
  -H 'Content-Type: application/json' \
  -d '{"user_id":"user-a1b2c3d4e5f6","role":"USER"}'
# 받은 access_token을 Authorization: Bearer {토큰} 헤더에 넣어 API를 부른다
```

## 도메인 컨트롤러 만들기

본보기는 테스트 소스의 [`example`](src/test/kotlin/com/gsmsv/api/example) 패키지다. 계약에 명세가 없는 테스트 전용 API이고, 실제 도메인은 이 모양을 그대로 따른다.
규칙의 원본은 [AGENTS.md](AGENTS.md)와 [GSMSV-Contracts-V2](https://github.com/GSMSV/GSMSV-Contracts-V2)다.

시작하기 전에 계약에 그 API의 OpenAPI 명세가 있는지 확인한다. **명세가 없는 API는 구현하지 않는다.**

### 1. 패키지

```text
com.gsmsv.api.{도메인}
├── controller   @RestController. 로더를 불러 자원 객체를 받고 service에 넘긴다
├── service      로더가 돌려준 자원 객체로 일한다. @Transactional은 여기에만
├── repository   JPA repository와 ServiceResourceReader 구현
├── domain       엔티티 (OwnedResource 구현)
└── dto          XxxRequest / XxxResponse
```

### 2. 엔티티와 reader

- 서비스 자원 엔티티는 `OwnedResource`(`id`, `owner`)를 구현한다. 소유자는 만든 뒤 바꾸지 않는다 (`updatable = false`)
- 새 id는 `IdGenerator.resourceId(ResourceType.X)`로 만든다
- `ServiceResourceReader<T>`를 구현한다 (`findById`, `findAll`, `findAllByOwners`). 이 reader는 로더와 `listVisible` 밖에서 부르지 않는다
- 예: [`Example`](src/test/kotlin/com/gsmsv/api/example/domain/Example.kt), [`InMemoryExampleReader`](src/test/kotlin/com/gsmsv/api/example/repository/InMemoryExampleReader.kt)

### 3. 컨트롤러

예: [`ExampleController`](src/test/kotlin/com/gsmsv/api/example/controller/ExampleController.kt)

```kotlin
@RequirePermission(Action.DELETE)            // 메서드마다 셋 중 하나만: @RequirePermission / @Authenticated / @Public
@DeleteMapping("/{grn}")
@ResponseStatus(HttpStatus.NO_CONTENT)
fun delete(
    @CurrentUser user: AuthenticatedUser,    // 인증된 요청자
    @PathVariable grn: Grn,                  // {grn}은 Grn으로 받는다. 형식이 틀리면 400 INVALID_GRN
) {
    // 어노테이션과 같은 Action을 넘긴다. 다르면 500
    val vm = loader.load(user.toActor(), grn.value, Action.DELETE, vmReader)
    vmService.delete(vm)                     // service에는 id가 아니라 자원 객체를 넘긴다
}
```

| 어노테이션 | 언제 | 비고 |
|---|---|---|
| `@RequirePermission(Action.X)` | 특정 자원·팀에 대한 동작 | 로더에 같은 `Action`을 넘긴다 |
| `@Authenticated` | 로그인만 필요 (내 정보, 목록) | 로더를 부르지 않는다 |
| `@Public` | 인증 없이 | `SecurityConfig.PUBLIC_PATHS`에도 경로를 추가한다 |

어노테이션이 없거나 둘 이상이면 앱이 뜨지 않는다.

로더는 자원 모양에 따라 고른다.

| 로더 | 받는 값 | 용도 |
|---|---|---|
| `ServiceResourceLoader.load` | GRN | VM·함수·DB·앱 같은 서비스 자원 |
| `SubResourceLoader.load` | 부모 GRN + 하위 id | 스냅숏 같은 하위 자원 |
| `TeamLoader.load` | 팀 id | 팀 관리, 팀 앞으로 자원 만들기 |
| `ServiceResourceLoader.listVisible` | - | 목록 조회 |

로더가 대신 끝내 주는 응답은 아래와 같다. 컨트롤러·service에서 소유자나 역할을 직접 비교하지 않는다.

| 상황 | 응답 |
|---|---|
| 토큰 없음·틀림 | 401 `UNAUTHENTICATED` |
| GRN·id 형식 오류 | 400 `INVALID_GRN` / `INVALID_RESOURCE_ID` |
| 엔드포인트와 자원 종류가 다름 | 400 `GRN_RESOURCE_TYPE_MISMATCH` |
| 없는 자원, 볼 수 없는 자원 | 404 `RESOURCE_NOT_FOUND` (둘을 구분하지 않는다) |
| 볼 수는 있지만 역할이 모자람 | 403 `ACCESS_DENIED` (`details.required_role`, `details.actor_role`) |
| 승인되지 않은 팀의 생성 | 403 `TEAM_NOT_APPROVED` |

### 4. 응답

- 서비스 자원 응답은 `ResourceResponse`를 구현한다. 엔티티를 그대로 내보내지 않는다
- `grn`은 DB 값으로 만든다: `GrnParser.format(owner.id, id)`. GRN 문자열을 `split`·`substring`으로 자르지 않는다
- `owner`는 `OwnerResponse.of(owner, 이름)`
- `permissions`는 `PermissionsResolver.resolve(actor, 자원, 동작 목록)`으로 만든다
- JSON 필드는 자동으로 snake_case가 된다 (`createdAt` → `created_at`)
- 예: [`ExampleResponse`](src/test/kotlin/com/gsmsv/api/example/dto/ExampleResponse.kt)

### 5. 목록

```kotlin
@Authenticated
@GetMapping
fun list(
    @CurrentUser user: AuthenticatedUser,
    @Valid @ModelAttribute page: CursorPageRequest,   // ?cursor=&limit= (기본 20, 최대 100)
): CursorPage<VmResponse> = vmService.list(user.toActor(), page)
```

- service는 `loader.listVisible(actor, reader)`로 볼 수 있는 자원만 꺼낸다
- repository는 `created_at` 내림차순, 같으면 `id` 내림차순으로 `limit + 1`개를 읽는다
- `CursorPage.of(rows, limit, { Cursor(it.createdAt, it.id) }) { toResponse(it) }`로 `{ items, next_cursor }`를 만든다
- 예: [`ExampleService.list`](src/test/kotlin/com/gsmsv/api/example/service/ExampleService.kt)

### 6. 오류 던지기

권한과 상관없는 도메인 규칙 위반은 `BusinessException`을 던진다. 공통 형식 `{code, message, request_id, details}`은 `GlobalExceptionHandler`가 만든다.

```kotlin
throw BusinessException(ErrorCode.INVALID_REQUEST, message = "잠긴 자원은 삭제할 수 없습니다.")
```

- 새 오류 code는 `ErrorCode`에 추가한다. 이름은 계약의 명세를 따른다
- `message`는 응답에 그대로 나간다. 요청자가 보내지 않은 자원 정보나 내부 값을 담지 않는다
- 요청 DTO 검증(`@Valid`) 실패는 400 `VALIDATION_FAILED`로 자동 처리된다

## 컨트롤러 테스트

`@ApiWebMvcTest`와 `TestTokens`를 쓴다 ([`support`](src/test/kotlin/com/gsmsv/api/support) 패키지). 실제 인증 필터와 오류 형식이 그대로 켜진다.

```kotlin
@ApiWebMvcTest(VmController::class)   // 테스트할 컨트롤러를 꼭 적는다
@Import(VmTestConfig::class)          // 로더·reader 등 도메인 빈
class VmControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
) {
    @Test
    fun `일반 팀원이 VM을 삭제하려 하면 403을 돌려준다`() {
        mockMvc
            .delete("/api/v2/vms/$TEAM_VM") { bearer(tokens.user(MEMBER)) }   // tokens.admin(...)도 있다
            .andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("ACCESS_DENIED") }
            }
    }
}
```

- `TestTokens`는 개발용 토큰 API와 같은 방법으로 토큰을 발급한다. 토큰 없이 보내면 401 경로를 시험할 수 있다
- 팀 도메인이 생기기 전까지 로더는 Spring 빈이 아니다. 테스트 설정에서 직접 만든다 ([`ExampleTestConfig`](src/test/kotlin/com/gsmsv/api/example/ExampleTestConfig.kt))
- 새 컨트롤러는 적어도 아래 경우를 시험한다 ([`ExampleControllerTest`](src/test/kotlin/com/gsmsv/api/example/controller/ExampleControllerTest.kt))

| 경우 | 기대 |
|---|---|
| 토큰 있음 | 200 |
| 토큰 없음 | 401 `UNAUTHENTICATED` |
| 틀린 GRN | 400 `INVALID_GRN` |
| 남의 자원 | 404 `RESOURCE_NOT_FOUND` |
| 팀원이 삭제 시도 | 403 `ACCESS_DENIED` |

```bash
./gradlew test --tests 'com.gsmsv.api.vm.*'   # 로컬에서는 관련 테스트만. 전체 테스트는 CI가 돌린다
./gradlew ktlintCheck
```

## 참여하기

- 협업 규칙: [CONTRIBUTING](.github/CONTRIBUTING.md)
- 보안 문제 신고: [SECURITY](.github/SECURITY.md)
