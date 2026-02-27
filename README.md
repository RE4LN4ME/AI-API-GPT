# AI-API-GPT

Spring Boot 기반 GPT 챗 API 서버입니다.  
대화 이력 저장, API Key 인증, SSE 스트리밍 응답, OpenAPI 문서, CI 스모크 검증을 제공합니다.

## 기술 스택
- Java 21
- Spring Boot 4.0.2
- Spring Web, WebFlux(WebClient), Validation, JPA
- PostgreSQL + Flyway
- Springdoc OpenAPI (Swagger UI)

## 주요 기능
- `POST /api/chat/completions`: 일반 채팅 응답
- `POST /api/chat/completions/stream`: SSE 스트리밍 응답
- 대화/메시지 이력 조회 및 삭제
- `X-API-Key` 기반 인증
- API Key 기준 Rate Limiting
- `/health`, `/actuator/health/readiness` 헬스체크

## 사전 준비
- JDK 21
- PostgreSQL
- OpenAI API Key

## 환경변수
필수:
- `DB_URL` (예: `jdbc:postgresql://localhost:5432/AI-API-GPT`)
- `DB_USERNAME`
- `DB_PASSWORD`
- `OPENAI_API_KEY`

선택:
- `OPENAI_MODEL` (기본값: `gpt-4o-mini`)
- `SPRING_PROFILES_ACTIVE` (기본값: local/default, 운영은 `prod`)
- `APP_RATE_LIMIT_ENABLED` (기본값: `true`)
- `APP_RATE_LIMIT_REQUESTS_PER_WINDOW` (기본값: `60`)
- `APP_RATE_LIMIT_WINDOW_SECONDS` (기본값: `60`)
- `OPENAI_FALLBACK_ON_RATE_LIMITED` (기본값: `false`)
- `APP_CORS_ALLOWED_ORIGINS` (쉼표 구분, 예: `https://app.example.com,https://admin.example.com`)

## 로컬 실행
```bash
./gradlew bootRun
```

Windows:
```powershell
.\gradlew.bat bootRun
```

기본 포트: `8081`

## 테스트 실행
```bash
./gradlew test
```

Windows:
```powershell
.\gradlew.bat test
```

## Docker 실행
이미지 빌드:
```bash
docker build -t ai-api-gpt:local .
```

컨테이너 실행:
```bash
docker run --rm -p 8081:8081 \
  -e DB_URL="jdbc:postgresql://host.docker.internal:5432/AI-API-GPT" \
  -e DB_USERNAME="<db-user>" \
  -e DB_PASSWORD="<db-pass>" \
  -e OPENAI_API_KEY="<openai-key>" \
  ai-api-gpt:local
```

## API 문서
- Swagger UI: `http://localhost:8081/swagger-ui`
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`

## 헬스체크
- `GET /health`
- `GET /actuator/health`
- `GET /actuator/health/readiness`

## 인증 헤더
보호 API 호출 시 아래 헤더가 필요합니다.
```http
X-API-Key: <your-api-key>
```

## API 빠른 예제
채팅 요청:
```bash
curl -X POST "http://localhost:8081/api/chat/completions" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <your-api-key>" \
  -d "{\"message\":\"안녕\"}"
```

대화 목록 조회:
```bash
curl "http://localhost:8081/api/conversations?page=0&size=20" \
  -H "X-API-Key: <your-api-key>"
```

## CI
- GitHub Actions workflow: `.github/workflows/ci.yml`
- `test` job: 단위/통합 테스트
- `smoke` job: PostgreSQL 서비스 + 앱 기동 + 헬스/인증 API 스모크 검증

## 운영 참고
- 스키마 변경은 Flyway migration으로만 관리
- `prod` 프로필에서 bootstrap user는 강제 비활성
- OpenAI 429는 기본적으로 `RATE_LIMITED`로 반환
