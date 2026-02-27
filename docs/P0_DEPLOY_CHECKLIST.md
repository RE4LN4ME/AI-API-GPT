# P0 Deployment Checklist

## 1) Deploy Target
- Platform: Railway or Render
- Runtime: Docker image (`ai-api-gpt`)
- Public base URL 확보 (예: `https://<service>.up.railway.app`)

## 2) Required Environment Variables
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `OPENAI_API_KEY`

Recommended:
- `SPRING_PROFILES_ACTIVE=prod`
- `OPENAI_MODEL=gpt-4o-mini`
- `OPENAI_FALLBACK_ON_RATE_LIMITED=false`
- `APP_RATE_LIMIT_ENABLED=true`
- `APP_RATE_LIMIT_REQUESTS_PER_WINDOW=60`
- `APP_RATE_LIMIT_WINDOW_SECONDS=60`
- `APP_CORS_ALLOWED_ORIGINS=https://<frontend-domain>`

## 3) Deployment Validation (Public)
Set base URL:
```bash
BASE_URL="https://<your-service-url>"
```

Health checks:
```bash
curl -i "$BASE_URL/health"
curl -i "$BASE_URL/actuator/health"
curl -i "$BASE_URL/actuator/health/readiness"
```

Swagger/OpenAPI checks:
```bash
curl -i "$BASE_URL/swagger-ui"
curl -i "$BASE_URL/v3/api-docs"
```

Auth API check (`X-API-Key` required):
```bash
curl -i "$BASE_URL/api/conversations"
curl -i -H "X-API-Key: <valid-api-key>" "$BASE_URL/api/conversations?page=0&size=20"
```

Expected:
- `/health`, `/actuator/health/readiness` => `200`
- missing API key => `401`
- valid API key => `200`

## 4) Smoke Chat Check
```bash
curl -i -X POST "$BASE_URL/api/chat/completions" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <valid-api-key>" \
  -d '{"message":"deploy smoke test"}'
```

Expected:
- success response (`200`) or quota-based `429 RATE_LIMITED`
- no `500`/`502` 반복 발생

## 5) Failure Triage
- `5xx` at startup: DB env vars / network / migration logs 확인
- `401` with valid key: stored API key hash/rotation 상태 확인
- `429` spike: OpenAI quota and app rate-limit settings 확인
- CORS issue: `APP_CORS_ALLOWED_ORIGINS` 값 확인

## 6) Release Record
- Deploy timestamp (UTC):
- Commit SHA:
- Base URL:
- Verifier:
- Health result:
- API smoke result:
