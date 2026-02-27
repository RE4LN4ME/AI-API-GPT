# P0 배포 체크리스트

## 1) 배포 대상 확정
- 플랫폼: Railway 또는 Render
- 런타임: Docker (`Dockerfile` 기반)
- 외부 접근 가능한 Base URL 확보
  - 예: `https://ai-api-gpt-xxxx.onrender.com`

## 2) 필수 환경변수
- `DB_URL` (JDBC 형식)
- `DB_USERNAME`
- `DB_PASSWORD`
- `OPENAI_API_KEY`
- `ADMIN_API_KEY`

권장:
- `SPRING_PROFILES_ACTIVE=prod`
- `OPENAI_MODEL=gpt-4o-mini`
- `OPENAI_FALLBACK_ON_RATE_LIMITED=false`
- `APP_RATE_LIMIT_ENABLED=true`
- `APP_RATE_LIMIT_REQUESTS_PER_WINDOW=60`
- `APP_RATE_LIMIT_WINDOW_SECONDS=60`
- `APP_CORS_ALLOWED_ORIGINS=https://<frontend-domain>`

## 3) 배포 후 공용 엔드포인트 검증
```bash
BASE_URL="https://<your-service-url>"
```

헬스체크:
```bash
curl -i "$BASE_URL/health"
curl -i "$BASE_URL/actuator/health"
curl -i "$BASE_URL/actuator/health/readiness"
```

문서 엔드포인트:
```bash
curl -i "$BASE_URL/swagger-ui"
curl -i "$BASE_URL/v3/api-docs"
```

기대 결과:
- `/health`, `/actuator/health/readiness` -> `200`
- `/swagger-ui` -> `302` 또는 `200` (`/swagger-ui/index.html` 리다이렉트 가능)
- `/v3/api-docs` -> `200`

## 4) 인증/권한 검증
일반 API (`X-API-Key`):
```bash
curl -i "$BASE_URL/api/conversations"
curl -i -H "X-API-Key: <valid-api-key>" "$BASE_URL/api/conversations?page=0&size=20"
```

관리자 API (`X-Admin-Key`):
```bash
curl -i -X POST "$BASE_URL/api/admin/keys" \
  -H "X-Admin-Key: <admin-api-key>"
```

기대 결과:
- API Key 누락 -> `401`
- 유효 API Key -> `200`
- 유효 Admin Key -> `200`

## 5) 채팅 스모크 검증
일반 채팅:
```bash
curl -i -X POST "$BASE_URL/api/chat/completions" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <valid-api-key>" \
  -d '{"message":"deploy smoke test"}'
```

스트리밍 채팅(SSE):
```bash
curl -N -X POST "$BASE_URL/api/chat/completions/stream" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <valid-api-key>" \
  -d '{"message":"stream smoke test"}'
```

기대 결과:
- 일반 채팅 -> `200` 또는 쿼터/레이트 제한 시 `429 RATE_LIMITED`
- 스트리밍 채팅 -> `event: token` 수신 후 `event: done`
- 반복적인 `500`/`502` 없어야 함

## 6) 장애 트리아지
- 시작 직후 `5xx`:
  - DB 환경변수 형식/값, Flyway 로그, 네트워크 접근 확인
- `UnknownHostException`:
  - `DB_URL` 호스트 오타 또는 잘린 값 확인
- `401` (유효 키인데 실패):
  - API Key 회전/폐기 여부, 해시 저장 상태 확인
- `429` 급증:
  - OpenAI 쿼터 + `app.rate-limit` 설정 동시 확인
- CORS 오류:
  - `APP_CORS_ALLOWED_ORIGINS`에 실제 프론트 origin 반영 여부 확인

## 7) 릴리스 기록
- Deploy timestamp (UTC):
- Commit SHA:
- Base URL:
- Verifier:
- Health 결과:
- 일반 API 검증 결과:
- 관리자 API 검증 결과:
- 채팅 스모크 결과:
