# Kotlin 코딩 규칙

## 언어

- `!!` 금지 (테스트 제외). `lateinit`은 테스트와 Spring 주입에만 쓴다
- `val`과 읽기 전용 컬렉션(`List`, `Map`)을 먼저 쓴다
- 주석은 한국어로 "왜"만 적는다. KDoc은 공개 API에만 쓴다

## Spring

- 생성자 주입만 쓴다 (필드 주입·setter 주입 금지)
- 요청·응답은 `data class` DTO로 만들고 이름은 `XxxRequest`/`XxxResponse`. 입력은 Bean Validation으로 검증한다
- `@Transactional`은 service에만 붙인다. 조회 메서드는 `@Transactional(readOnly = true)`
- 예외는 `ErrorCode` enum + `BusinessException` + `@RestControllerAdvice`로 처리한다. 응답 형식은 Contracts-V2를 따른다
- 로깅은 kotlin-logging. 토큰·비밀번호·내부 IP는 로그에 남기지 않는다

## 설정

- 설정 파일은 `application.yml`. 비밀값은 환경변수로만 받는다 (`${DB_PASSWORD}` 형태, 기본값에 실제 값 금지)

## JPA

- 엔티티는 `data class` 금지
- 상태는 엔티티 메서드로만 바꾼다 (외부에서 필드에 직접 대입하지 않는다)
- 연관관계는 기본 `FetchType.LAZY`

## Flyway

- 파일 이름은 `V{번호}__{설명}.sql` (밑줄 2개)
- develop에 병합된 마이그레이션은 수정하지 않는다. 고칠 게 있으면 새 버전을 추가한다

## 의존성

- 의존성 추가나 버전 변경은 사용자 확인 후에만 한다
