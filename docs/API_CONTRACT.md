# API Contract

## Base
- Base path: `/api`
- Auth header: `X-API-Key`
- Content type: `application/json`
- Request trace header:
  - Request: optional `X-Request-Id`
  - Response: always includes `X-Request-Id`

## Endpoints

### GET `/health`
- Response `200`:
```json
{
  "status": "UP"
}
```

### POST `/users/register`
- Auth: none
- Request body: none
- Response `200`:
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "apiKey": "ak_xxxxxxxxxxxxxxxxxxxxxxxx"
  },
  "error": null
}
```

### POST `/chat/completions`
- Request:
```json
{
  "message": "string (1..4000)",
  "conversationId": 1
}
```
- Response `200`:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "role": "assistant",
    "content": "string",
    "createdAt": "2026-01-01T00:00:00Z"
  },
  "error": null
}
```

### POST `/chat/completions/stream`
- Request: same as `/chat/completions`
- Response `200`:
  - `Content-Type: text/event-stream`
  - SSE events:
    - `event: token` + partial text chunk
    - `event: done` + `[DONE]`
    - `event: error` + error message (when stream fails)

### GET `/conversations?page=0&size=20`
- Query:
  - `page` >= 0
  - `size` 1..100
- Response `200`:
```json
{
  "success": true,
  "data": {
    "items": [],
    "page": 0,
    "size": 20,
    "total": 0,
    "hasNext": false
  },
  "error": null
}
```

### GET `/conversations/{id}`
- Path:
  - `id` >= 1
- Response `200`: `data` is `ConversationDto`

### GET `/conversations/{id}/messages?page=0&size=50`
- Path:
  - `id` >= 1
- Query:
  - `page` >= 0
  - `size` 1..200
- Response `200`:
```json
{
  "success": true,
  "data": {
    "items": [],
    "page": 0,
    "size": 50,
    "total": 0,
    "hasNext": false
  },
  "error": null
}
```

### DELETE `/conversations/{id}`
- Path:
  - `id` >= 1
- Response `200`:
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

## Error Model
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "BAD_REQUEST",
    "message": "string"
  }
}
```

## Error Codes
- `BAD_REQUEST`: validation/input errors
- `UNAUTHORIZED`: missing or invalid API key
- `NOT_FOUND`: resource not found or no ownership
- `RATE_LIMITED`: server rate limit exceeded or OpenAI quota exceeded (`429`)
- `UPSTREAM_ERROR`: external AI/network failure
- `INTERNAL_ERROR`: unexpected server error
