# devbook-user-service

devbook-commerce의 **회원** 서비스입니다. Spring Boot 4.1.1 / Java 21.

- API 계약: [devbook-platform/docs/api-contract.md](../devbook-platform/docs/api-contract.md) — 정본은 플랫폼 저장소
- 배포 매니페스트: [devbook-platform/k8s/apps/user-service.yaml](../devbook-platform/k8s/apps/user-service.yaml)

## 엔드포인트
- `POST /api/users` 가입 (비밀번호 8~72자, bcrypt 해시 저장)
- `POST /api/auth/login` · `/refresh` · `/logout` — 액세스 토큰(JWT ES256, 15분) + 리프레시 쿠키(HttpOnly, 14일, 회전)
- `GET /api/users/me` 내 정보 (인증 필요 — Gateway가 붙인 `X-User-Id` 사용)
- `GET /.well-known/jwks.json` 공개키 (클러스터 내부 전용, Gateway가 토큰 검증에 사용)

## 개발
```bash
make wrapper   # 최초 1회: Gradle Wrapper 생성 (커밋 대상)
make test      # Docker 안에서 테스트 (로컬 JDK 불필요)
make image     # devbook/user-service:dev 이미지 빌드
```
클러스터 배포는 devbook-platform에서 `make images deploy`로 합니다.

## 설정 (환경변수)
| 이름 | 설명 |
|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | user-db 접속 정보 (K8s Secret `user-db-credentials`에서 주입) |
| `/etc/devbook/jwt/private.pem`, `public.pem` | JWT 서명키 (K8s Secret `user-jwt-signing-key`를 마운트). 없으면 기동 실패 |
| `DEVBOOK_AUTH_COOKIESECURE` | 리프레시 쿠키 `Secure` 여부. 기본 true, 로컬 http 에서만 false |

