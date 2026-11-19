# 계약 (map-project-user)

이 서비스가 앱·다른 서비스와 주고받는 경로, 전송 이름, 필드, 오류 형식, 한도, 소유 관계를 적는다. 코드가 기준이며, 아래 표의 근거 파일을 함께 본다.

## 1. 앱 ↔ user (HTTP, `/api/v1/**`)

- 포트 `8080`(`src/main/resources/application.yml` 의 `server.port`). JSON, 응답 키는 대부분 스네이크 케이스다.
- **인증 경로의 요청 본문은 카멜 케이스다**(`domain/auth/dto/request/*`, `domain/auth/apple/AppleController.java`): 로그인 `{email, password, deviceType?}`, 가입 `{email, password, nickname, birthDate, gender, deviceType?}`, 카카오 `{accessToken, deviceToken?, deviceType?}`, 갱신 `{refreshToken}`, Apple `{identityToken, authorizationCode, …}`. 프로필 수정(`PATCH /users/me`)의 생년월일도 `birthDate`(`YYYY-MM-DD`).
- 인증: `Authorization: Bearer <access token>`(RS256 JWT). access 기본 1시간, refresh 기본 30일(`USER_JWT_ACCESS_TTL`·`USER_JWT_REFRESH_TTL`).
- 인가: `USER_AUTH_ENFORCED=false`(기본)이면 아래 '항상 인증' 경로 말고는 토큰 없이도 열린다. `true` 이면 인증 경로를 뺀 전부가 인증 필수다(`global/config/SecurityConfig.java`). 소유자 확인·서비스 약관 동의·18세 이상 확인은 이 값과 무관하게 서비스 계층에서 한다.

| 묶음 | 경로 | 비고 |
|---|---|---|
| 인증 | `POST /auth/signup`·`/auth/login`·`/auth/token/refresh`·`/auth/logout` | 이메일·비밀번호(BCrypt). refresh 는 세션 계열 단위로 회전하고, 재사용된 refresh 는 그 계열을 폐기한다 |
| 카카오 | `POST /auth/kakao/callback` | 앱이 카카오 SDK 로 받은 **액세스 토큰**을 보낸다. 서버는 토큰 정보 API 로 앱 번호(`USER_KAKAO_APP_ID`)가 맞는지 확인하고 토큰은 저장하지 않는다. 앱 번호가 비면 꺼진다 |
| Apple | `GET /auth/apple/nonce`, `POST /auth/apple/callback` | `USER_APPLE_ENABLED=true` 일 때. identity token 의 대상(`USER_APPLE_CLIENT_ID`)·nonce 를 검사한다 |
| 항상 인증 | `GET·PATCH·DELETE /users/me`, `/moderation/**`, `GET·POST /consents`, `/consents/ai/**` | `DELETE /users/me` 는 탈퇴 |
| 추천 | `POST /recommend`, `GET /recommend/{jobId}`, `POST /recommend/{jobId}/edit`, `POST /recommend/{jobId}/research`, `POST /recommend/route` | 비동기: 접수 202, 결과 조회는 준비 전 202 + `Retry-After: 3` |
| trip(동기) | `POST /trip/generate`·`/trip/route`·`/trip/research`·`/trip/replan` | 완료까지 기다려 `TripGenerateResponse` 를 200 으로 준다(대기 상한 `USER_TRIP_POLL_TIMEOUT_SECONDS`, 기본 110초) |
| 일정 | `GET·POST /schedules`, `GET·PUT·DELETE /schedules/{id}`, `POST /schedules/{id}/start`·`/arrivals`·`/replan`·`/weather-alert/dismiss` | 소유자만 |
| 주변 장소 | `GET /schedules/{id}/nearby`, `POST /schedules/{id}/nearby/click` | hub 에 봉투로 묻는다 |
| 장소·리뷰 | `GET /places/search`·`/places/address`·`/places/photos`, `GET /reviews`, `GET·POST /reviews/summary` | 요약 `GET` 은 캐시만 읽고, `POST` 는 명시 동의(`consent:true`, `client_request_id`)가 있어야 만든다 |
| 이동 | `GET /transit/routes`·`/transit/subway`, `POST /transit/routes/lane`·`/transit/routes/walk`, `GET /mobility/bike-stations`·`/mobility/pm-vehicles` | `/transit/routes/walk` 는 기본 꺼짐(미완성 실험 기능, `USER_TRANSIT_WALK_ENABLED`) |
| 날씨 | `GET /weather/home`, `GET /weather/forecast` | |
| 동의 | `GET·POST /consents`(약관·개인정보·18세 이상), `GET /consents/ai`, `POST·DELETE /consents/ai/{scope}` | AI 범위: `trip`·`vision`·`review_summary`. 판 값은 `1.0.0` |
| 채팅 | `/chat/rooms/**`, `/chat/invites/{token}`, `/chat/invites/{token}/join` | 아래 3절 |
| 신고·차단 | `POST /moderation/reports`, `GET /moderation/reports`, `GET·PUT·DELETE /moderation/blocks/...` | |
| 비전 허가 | `POST /vision/permit` | 비전 서비스가 사용자 JWT 와 내부 토큰으로 부른다. `{consume:false}` 는 연결 확인, `{consume:true}` 는 하루 한도 소비. 소진 429 |

### 오류 형식
```json
{"timestamp": "…", "status": 409, "code": "RECOMMEND_001", "message": "…"}
```
`code` 는 `global/exception/ErrorCode.java` 에 모두 있다. 상류 서비스의 오류 본문은 그대로 넘기지 않고 고정 문구로 바꾼다.

### 추천 실패 코드
추천 결과(`GET /recommend/{jobId}`)는 실패해도 HTTP 200 에 `status:"failed"`, `code`, `retryable` 을 싣는다. trip 동기 경로는 같은 코드를 HTTP 상태로 돌려준다.

| code | trip HTTP | retryable |
|---|---|---|
| `no_matching_places` | 422 | false |
| `selection_invalid` | 502 | false |
| `invalid_request` | 422 | false |
| `upstream_unavailable` | 503 | 일시 장애가 분명할 때만 true |
| `quota_exceeded` | 429 | false |
| `generation_timeout` | 504 | 작업자 쪽 시간초과일 때 true |
| `generation_failed` | 502 | false |

- trip 의 대기 상한만 넘긴 경우는 504 `recommendation_pending`(작업은 계속 돌 수 있어 `retryable:false`).
- `retryable` 은 같은 입력으로 나중에 다시 요청해도 된다는 뜻일 뿐이다. 서버·앱은 이 값을 보고 조건을 바꾸거나 자동으로 다시 요청하지 않는다.

### 한도
| 대상 | 한도 | 근거 |
|---|---|---|
| 인증 경로 | 로그인 10·가입 5·갱신 20·카카오 10·Apple nonce 10·Apple 콜백 10 회 / 60초, 발신 주소별 | `global/ratelimit/RateLimitFilter.java` |
| 재탐색 | 계정당 하루 3회(KST 자정 초기화) | `USER_RESEARCH_DAILY_LIMIT` |
| 리뷰 요약 명시 요청 | 사용자당 60초에 6건, 같은 요청 ID 의 결과는 30분 재사용 | `places/ReviewSummaryGenerationService.java` |
| 채팅 전송 | 10초에 20건, 메시지 2000자(Java `String.length()` — UTF-16 코드 단위, `chat/service/ChatMessageService.java:173`), 방 10명 | `USER_CHAT_*` |
| 비전 허가 | 사용자당 하루 60(기본, 개발·시험용으로 낮게 둔 값) | `USER_VISION_DAILY_LIMIT` |
- 주소 헤더(`X-Real-IP`)는 직전 발신자가 신뢰 프록시 대역(`USER_RATELIMIT_TRUSTED_PROXIES`)일 때만 믿는다.

## 2. user → agent·hub (HTTP, 내부)

| 상대 | 기본 주소 | 경로 | 시간 제한 |
|---|---|---|---|
| agent | `MAP_PROJECT_AGENT_BASE_URL`(`http://agent:8000`) | `POST /v1/recommend`(접수 — 결과는 4절 스트림), `POST /v1/reviews/summary`, `POST /v1/reviews/summary/batch`(선작성) | `USER_AGENT_TIMEOUT_SECONDS`(60초) |
| hub | `MAP_PROJECT_HUB_BASE_URL`(`http://hub:8000`) | `/v1/weather`·`/v1/weather/now`·`/v1/places`·`/v1/places/photos`·`/v1/places/nearby`·`/v1/reviews`·`/v1/transit/*`·`/v1/mobility/*`, `POST /v1/directions/batch` | `USER_HUB_TIMEOUT_SECONDS`(5초), 길찾기 묶음 `USER_HUB_DIRECTIONS_TIMEOUT_SECONDS`(20초) |

- 내부 토큰: `MAP_PROJECT_INTERNAL_SERVICE_TOKEN` 이 있으면 `X-Internal-Token` 으로 붙인다(비면 붙이지 않음).
- **좌표는 늘 봉투(`loc`)로만 보낸다.** 형식 `v1.<iv>.<ct>`(base64url), AES-256-GCM, AAD `map|loc|v1`, 열쇠 `MAP_PROJECT_LOCATION_WIRE_KEY`(user·agent·hub 같은 값). 봉투 안에는 만든 시각 `iat` 가 들어가고, 여는 쪽은 나이 상한을 본다(user 는 `USER_LOCATION_WIRE_MAX_AGE_SECONDS`, 기본 3600초).
  - 위치 하나: `loc={lat,lng}` / 출발·도착: `loc={start_lat,start_lng,end_lat,end_lng}` / 길찾기 묶음: `{mode, loc={legs}}` / agent 에 보내는 고른 장소: `loc={places}`.
  - hub 의 주변 장소 응답만 `{loc}` 봉투로 오고 user 가 연다.
- 추천 요청의 `stage` 는 `init`·`reexplore`·`route` 다. 앱이 보낸 값은 믿지 않고 서버가 다시 정한다. 재탐색은 `exclude`(최대 50개)에 이미 본 장소를 담는다.
- 길찾기 묶음은 한 요청에 구간 20개까지 나눠 보낸다(hub 상한).

## 3. 실시간 채팅 (STOMP over WebSocket)

- 핸드셰이크 `/ws/chat`(`USER_CHAT_WS_ENDPOINT`), CONNECT 에 JWT.
- 보내기 `/app/rooms/{roomId}/send`·`/read`·`/typing`, 받기 `/topic/rooms/{roomId}`, 보낸 사람 한 명에게 가는 오류 `/user/queue/errors`.
- 구독 목적지는 브로커와 같은 대조기로 판정해, 참가하지 않은 방의 토픽에 패턴으로 붙을 수 없다. 하트비트 10초.
- 인스턴스 사이 팬아웃은 Redis 발행/구독 채널 `chat:broadcast`(`USER_CHAT_BROADCAST_CHANNEL`).
- 초대: `POST /chat/rooms/{roomId}/invite` 는 `{token, url, version, expires_at}` 를 준다.
  - **배포자는 user 의 초대 기준 주소(`USER_CHAT_INVITE_BASE_URL`)와 client 의 초대 origin 을 같은 origin 으로 준다. user 의 기준 주소가 비어 있으면 초대 응답의 url 은 null 이고 token 만 유효하다. 이때 client 는 token 으로 앱 전용 주소(<스킴>://invite/<token>)를 만들어 공유하며, 이 주소는 앱이 설치된 기기에서만 열린다.**

## 4. agent → user (Redis Streams)

| 이름 | 기본값 | 근거 |
|---|---|---|
| 결과 스트림 | `agent:jobs:done`(`MAP_PROJECT_STREAM_NAME`) | agent 가 쓰고 user 가 읽는다 |
| 소비자 그룹 | `bff-result`(`MAP_PROJECT_STREAM_GROUP`) | |
| DLQ | `agent:jobs:done:dlq`(`MAP_PROJECT_STREAM_DLQ`) | 재시도(`USER_STREAM_MAX_RETRY`, 3) 초과분 |
| 논리 DB | 2(`MAP_PROJECT_REDIS_DB_STREAMS`) | |

- 메시지 필드: `job_id`, `status`(`done`·`failed`), `payload`, `training`(학습 수집을 켰을 때만).
- `payload` 는 늘 봉투다: `seal({"payload": "<본문 JSON 문자열>"})`. **봉투가 아닌 본문은 저장하지 않고 버린다**(경고 로그).
- user 는 PostgreSQL 에 완료를 기록한 뒤에만 ack 한다. 기록이 실패하면 ack 하지 않아 PEL 에 남고, 재회수(`USER_STREAM_MIN_IDLE_MS`) 뒤 다시 처리되며, 재시도가 다하면 DLQ 로 간다.
- `training.schema_version` 은 정수(옛 표기)와 판 문자열(예: `"1.0.0"`)을 모두 받는다. 판 표기 모양(`[0-9A-Za-z._-]{1,32}`)이 아니면 그 신호만 거른다.

## 5. 운영 콘솔 → user (`/internal/admin/**`)

- 사설 대역(`USER_INTERNAL_TRUSTED_CIDRS`)에서 온 요청이고 `X-Internal-Token` 이 `MAP_PROJECT_USER_ADMIN_INTERNAL_TOKEN` 과 같을 때만 통과한다. 값이 비었거나 일반 서비스 토큰과 같으면 닫힌다.
- 회원 조회(`/users`), 추천 작업 조회·통계(`/recommend-jobs`), DLQ 조회·재처리·폐기(`/dlq`), 신고 처리(`/moderation/reports/**`). 신고 조치 주체는 `X-Admin-Actor` 헤더로 받는다.

## 6. 데이터베이스와 소유

- 스키마 `user_service` 하나만 쓴다. 다른 스키마에 쓰지 않는다.
- 역할: `map_user_owner`(객체 소유, 로그인 불가), `map_user_migrator`(마이그레이션, `SET ROLE map_user_owner`), `map_user_runtime`(서빙, DML 만). 서빙은 `map_user_runtime` 이 아니면 기동하지 않는다.
- 마이그레이션은 서빙이 하지 않는다. 순서는 [architecture.md](architecture.md) 의 '데이터베이스 준비' 를 본다.
