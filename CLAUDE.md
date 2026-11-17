# devbook-user-service

devbook-commerce의 회원 서비스. 전체 맥락·결정은 `../devbook-platform/CLAUDE.md`와 `../devbook-platform/docs/adr/`.

## 하드 제약
- API 계약 정본은 `../devbook-platform/docs/api-contract.md`. 계약을 바꾸려면 그 문서부터 고친다(이 저장소 단독으로 바꾸지 않는다).
- 이 서비스는 **자기 DB(userdb)만** 쓴다. 다른 서비스 데이터는 API로만 묻는다.
- 다른 서비스와 코드 공유 모듈을 만들지 않는다. `ErrorResponse` 등 중복은 의도된 것.
- 금액은 `long`(원), 시각은 `Instant`(UTC, DB 정밀도에 맞춰 마이크로초).
- JPA `ddl-auto: validate`. 스키마 변경은 새 Flyway 파일로만 (기존 마이그레이션 수정 금지).
- 시크릿을 저장소에 넣지 않는다. JWT 서명키도 마찬가지 (K8s Secret으로만).
- 인증 규약은 `../devbook-platform/docs/adr/0005-authentication-jwt.md`. 특히:
  - 비밀번호는 `PasswordEncoder`(Delegating)로만 다룬다. 원문·해시를 로그에 남기지 않는다.
  - 리프레시 토큰 원문은 쿠키로만 내보내고 DB에는 SHA-256 해시만 저장한다.
  - 로그인 실패는 "이메일 없음"과 "비밀번호 틀림"을 구분하지 않는다(응답·시간 모두).
  - 신원은 `X-User-Id` 헤더(Gateway가 JWT에서 만든 값)로만 판단한다. 본문·쿼리의 회원 id를 믿지 않는다.


## 명령
`make test`, `make image` (Docker 안에서 빌드).
