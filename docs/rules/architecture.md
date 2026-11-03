# 패키지 구조

## 패키지

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
│   ├── grn             GRN 파서 (security.md)
│   └── authorization   자원 로더, 규칙표, listVisible (security.md)
└── infra/              Infra-V2 연동 클라이언트
```

## 의존 방향

- controller → service → repository. 반대 방향은 없다
- 다른 도메인의 repository를 직접 부르지 않는다. 그 도메인의 service를 통해 호출한다
- 엔티티는 controller 밖으로 내보내지 않는다. 응답은 DTO로 바꿔서 돌려준다
- Infra-V2 호출은 `infra` 패키지의 클라이언트로만 한다. 각 도메인 service가 부르고, 권한 로더를 통과한 뒤에만 부른다

## API 명세 우선

- API는 [Contracts-V2](https://github.com/GSMSV/GSMSV-Contracts-V2)의 OpenAPI 명세를 먼저 쓴다
- **명세가 없는 API는 구현하지 않는다.** 명세가 없으면 사용자에게 Contracts-V2 PR을 먼저 올리자고 제안한다
