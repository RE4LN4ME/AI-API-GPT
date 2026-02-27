# P0 운영 런북

## 1) OpenAI 쿼터 복구
- OpenAI 대시보드에서 프로젝트 결제/쿼터 상태를 확인합니다.
- API 키가 `chat.completions` 권한을 가지고 있는지 확인합니다.
- 스모크 테스트를 실행합니다.
  - `GET /api/conversations` 응답이 `200`이어야 합니다.
  - `POST /api/chat/completions` 응답이 `200`이어야 합니다(`429`가 아니어야 함).

## 2) API 키 로테이션
- OpenAI 대시보드에서 기존 키를 폐기합니다.
- 새 키를 발급한 뒤 아래 값을 함께 갱신합니다.
  - OS 환경변수: `OPENAI_API_KEY`
  - 로컬 `.env`: `OPENAI_API_KEY`
- 애플리케이션 재시작 후 검증합니다.
  - 잘못된 키: `401 UNAUTHORIZED`
  - 올바른 키: `/api/conversations` 응답 `200`
- 키 동기화 점검 스크립트:
  - `powershell -ExecutionPolicy Bypass -File .\scripts\check-key-sync.ps1`

## 3) Flyway 단일 스키마 관리
- `spring.jpa.hibernate.ddl-auto=validate`를 유지합니다.
- Flyway 활성 상태를 유지합니다.
  - `spring.flyway.enabled=true`
  - `spring.flyway.locations=classpath:db/migration`
- 이력 테이블이 없는 기존 DB의 경우:
  - 최초 1회 `spring.flyway.baseline-on-migrate=true`로 실행
  - 이후 스키마 변경은 `db/migration`만 사용

## 4) 부트스트랩 사용자 안전 설정
- 운영 기본값:
  - `app.bootstrap-user.enabled=false`
- 로컬에서 필요할 때만 부트스트랩 활성화:
  - `app.bootstrap-user.enabled=true`
  - `app.bootstrap-user.api-key=<local test key>`

## 5) 에러 정책
- `400 BAD_REQUEST`: 입력값/검증/헤더 오류
- `401 UNAUTHORIZED`: API 키 인증 실패
- `404 NOT_FOUND`: 리소스 없음 또는 소유권 없음
- `429 RATE_LIMITED`: OpenAI 쿼터/레이트 제한 초과
- `502 UPSTREAM_ERROR`: 외부 AI/네트워크 장애
- `500 INTERNAL_ERROR`: 서버 내부 예외

## 6) 스모크 테스트 명령
- 서버를 기동한 뒤(프로필/포트 확인) 아래 명령을 실행합니다.
  - `powershell -ExecutionPolicy Bypass -File .\scripts\smoke-chat.ps1 -BaseUrl http://localhost:8081 -Count 3`

## 7) 헬스체크 및 준비 상태
- 기본 헬스체크:
  - `GET /health` => `200 {"status":"UP"}`
- Actuator 헬스체크:
  - `GET /actuator/health` => `200`
  - `GET /actuator/health/liveness` => `200`
  - `GET /actuator/health/readiness` => `200`

## 8) 요청 추적 정책
- 클라이언트는 `X-Request-Id`를 전달할 수 있습니다.
- 서버는 `X-Request-Id`를 응답 헤더에 그대로 반환합니다.
- 헤더가 없으면 서버가 `X-Request-Id`를 자동 생성합니다.
- 로그에는 요청 상관관계를 위해 `traceId`가 포함됩니다.

## 9) CI 스모크 게이트
- 워크플로 파일: `.github/workflows/ci.yml`
- `test` 잡:
  - `./gradlew test` 실행
- `smoke` 잡:
  - PostgreSQL 서비스 컨테이너 기동
  - `./gradlew bootRun` 실행
  - `GET /health`, `GET /actuator/health` 검증
  - 인증 포함 `GET /api/conversations` 응답 `200` 검증

## 10) OpenAI 429 정책 기본값
- 기본값: `openai.fallback-on-rate-limited=false`
- 의미: OpenAI가 `429`를 반환하면 서버도 `429 RATE_LIMITED`로 응답
- 운영에서 fallback 문구 응답이 필요할 때만 환경변수로 명시적으로 활성화
