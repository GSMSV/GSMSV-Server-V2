# 테스트 규칙

## 로컬에서 돌리는 범위

로컬에서는 관련 테스트만 돌린다. 전체 테스트는 CI에 맡긴다.

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

## 스타일

- JUnit 5 + MockK(springmockk). Mockito는 쓰지 않는다
- 테스트 이름은 백틱 한국어: ``fun `만료된 토큰이면 401을 돌려준다`()``
- 테스트 코드에서는 `!!`, `lateinit`을 써도 된다
- GRN 파서 테스트는 Contracts-V2의 맞는/틀린 id 목록을 그대로 쓴다 ([security.md](security.md))
