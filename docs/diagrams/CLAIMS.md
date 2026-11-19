# 그림 근거표

그림의 노드·엣지마다 무엇을 주장하는지와 그 근거를 적는다. 경로는 저장소 루트 기준, `M/` 은 `src/main/java/map/project/user/` 다. 원본은 `src/`, 다시 그리기는 `sh scripts/render_diagrams.sh`.

## bff-architecture — 구성

| 그림 | 요소(노드/엣지) | 주장 | 근거 file:line | 확인 방법 |
|---|---|---|---|---|
| bff-architecture | user 묶음 | Spring Boot, 포트 8080 | `src/main/resources/application.yml:293` | 설정 확인 |
| bff-architecture | REST /api/v1/** | 앱 HTTP 진입점 | `M/global/config/SecurityConfig.java`(경로 규칙), [contracts.md](../contracts.md) 1절 | 컨트롤러 목록 |
| bff-architecture | STOMP /ws/chat | 채팅 핸드셰이크 경로 | `M/chat/config/ChatWebSocketConfig.java:67`, `application.yml:287` | 설정 확인 |
| bff-architecture | /internal/admin/** | 사설 대역 + 전용 토큰 | `M/global/config/InternalAdminSecurityConfig.java:14-18`, `application.yml:115-116` | 설정 확인 |
| bff-architecture | 스트림 소비자 | `agent:jobs:done` 을 그룹 `bff-result` 로 읽는다 | `application.yml:220-221`, `M/recommend/RecommendJobsConsumer.java:373` | 설정·코드 확인 |
| bff-architecture | 앱 → REST "HTTPS + JWT" | Bearer access 토큰 | `application.yml:320-321`(TTL) | `JwtAuthenticationFilter` |
| bff-architecture | 앱 → STOMP "WebSocket" | STOMP over WebSocket | `M/chat/config/ChatWebSocketConfig.java:67-85` | 코드 확인 |
| bff-architecture | 비전 서비스 → REST "/vision/permit" | 비전 서비스가 하루 사용 허가를 받는다 | `M/vision/VisionPermitController.java:20,57` | 코드 확인 |
| bff-architecture | 운영 콘솔 → /internal "X-Internal-Token" | 전용 토큰 헤더 | `M/global/config/InternalAdminProperties.java:18,31` | 코드 확인 |
| bff-architecture | REST → agent "POST /v1/recommend, 고른 장소는 loc 봉투" | 접수는 HTTP, 고른 장소는 봉투 | `M/recommend/AgentClient.java:61,76` | 코드 확인(`RecommendServiceTest` 는 이 클라이언트를 모의 객체로 쓴다) |
| bff-architecture | REST → hub "좌표는 loc 봉투" | hub 호출의 좌표는 봉투 | `M/weather/HubWeatherNowClient.java:51`, `M/trip/HubDirectionsClient.java:48` | `TestLocationSeals.coordinatesAreSealed` |
| bff-architecture | REST → Kakao·Apple "토큰 확인" | 카카오 토큰 정보·사용자 정보, Apple identity token | `application.yml:342-343`, `M/domain/auth/apple/AppleProviderClient.java:34` | `KakaoOAuthServiceTest`, `AppleProviderClientTest` |
| bff-architecture | agent → Redis "결과 XADD(봉투)" | agent 가 결과를 봉투로 싣는다 | `M/recommend/RecommendJobsConsumer.java:373-390`(봉투만 연다), [contracts.md](../contracts.md) 4절 | `RecommendJobsConsumerTest.unsealedPayloadIsDroppedWithoutPersisting` |
| bff-architecture | 소비자 → Redis "XREADGROUP bff-result" | 소비자 그룹으로 읽는다 | `application.yml:220-221` | 설정 확인 |
| bff-architecture | STOMP → Redis "chat:broadcast" | 인스턴스 사이 팬아웃 | `application.yml:286`, `M/chat/ws/ChatBroadcastRelay.java:51,62` | 코드 확인 |
| bff-architecture | REST → Redis "폐기 목록·한도·초안·캐시" | 용도별 논리 DB | `application.yml:247-254` | 설정 확인 |
| bff-architecture | REST → PostgreSQL "map_user_runtime(DML)" | 서빙은 런타임 역할로만 붙는다 | `application.yml:39-40`, `M/global/config/RuntimeDatabaseGuard.java:34` | `RuntimeDatabaseGuardTest` |
| bff-architecture | /internal → PostgreSQL "조회·조치" | 회원·작업 조회, 신고 조치 | `M/admin/AdminUserQueryService.java`, `M/moderation/InternalModerationController.java` | 코드 확인 |
| bff-architecture | 학습 내보내기 진입점(기본 꺼짐) → PostgreSQL "읽기 전용" | 서빙과 따로 뜨는 진입점, 두 스위치가 켜져야 돈다 | `src/main/java/map/export/TrainingExportApplication.java:32`, `application.yml:409-412` | `TrainingExportIsolationTest` |
| bff-architecture | PostgreSQL 17 / Redis 7 | 판 | `docker-compose.yml:4,21`, `.github/workflows/ci.yml`(postgres:17 digest) | 설정 확인 |

## auth-flow — 로그인

| 그림 | 요소(노드/엣지) | 주장 | 근거 file:line | 확인 방법 |
|---|---|---|---|---|
| auth-flow | 이메일: POST /auth/signup·/auth/login | 이메일·비밀번호 가입·로그인 | `M/domain/auth/controller/AuthController.java` | `AuthServiceTest` |
| auth-flow | BCrypt 확인, refresh 해시 저장 | 비밀번호 BCrypt, refresh 는 해시로 저장 | `V004__init_user_service.sql:64-71`(`token_hash`) | `AuthServiceTest` |
| auth-flow | access(1시간) + refresh(30일) | 기본 TTL | `application.yml:320-321` | 설정 확인 |
| auth-flow | 카카오: POST /auth/kakao/callback (액세스 토큰) | 앱 SDK 토큰을 받는다 | `M/domain/auth/controller/AuthController.java:42` | `KakaoOAuthServiceTest` |
| auth-flow | 토큰 정보: 앱 번호 확인 / 사용자 정보 | 두 카카오 API | `application.yml:338-343` | `KakaoPropertiesTest` |
| auth-flow | 연결된 계정 찾기·만들기(토큰 저장 안 함) | 토큰을 저장하지 않는다 | `M/domain/auth/service/KakaoOAuthService.java` | `KakaoTransactionRollbackTest` |
| auth-flow | Apple: GET /auth/apple/nonce | nonce 발급 | `M/domain/auth/apple/AppleController.java:31` | `AppleChallengeStoreTest` |
| auth-flow | 발급자·대상·nonce·만료 검증 | identity token 검증 | `M/domain/auth/apple/AppleProviderClient.java:34` | `AppleProviderClientTest` |
| auth-flow | 인가 코드 교환 | Apple 토큰 교환 | `M/domain/auth/apple/AppleAccountService.java:44` | `AppleAccountServiceTest` |
| auth-flow | Apple refresh 토큰 암호화 저장 | 암호문으로 저장 | `M/domain/auth/apple/AppleAccountService.java:74-88` | `AppleAccountServiceTest` |
| auth-flow | 갱신: 세션 계열 안에서 회전, 재사용되면 계열 폐기 | refresh 회전 | `M/domain/auth/controller/AuthController.java:57`, `V020__refresh_session_family.sql` | `RefreshSessionConcurrencyTest` |
| auth-flow | 로그아웃: access 를 Redis 폐기 목록에 | 폐기 목록 | `M/domain/auth/service/AuthService.java:114`, `application.yml:248`(DB 1) | `AuthServiceTest` |
| auth-flow | 인증 경로 요청 한도(그림 밖 전제) | 로그인 10회/60초 등 | `M/global/ratelimit/RateLimitFilter.java:24-29` | 코드 확인 |

## chat-flow — 채팅

| 그림 | 요소(노드/엣지) | 주장 | 근거 file:line | 확인 방법 |
|---|---|---|---|---|
| chat-flow | CONNECT /ws/chat (JWT) | 접속 때 JWT 확인 | `M/chat/ws/StompAuthChannelInterceptor.java:15,44` | `ChatStompAuthInterceptorTest` |
| chat-flow | SUBSCRIBE /topic/rooms/{roomId} | 방 토픽 | `M/chat/ws/ChatBroadcastRelay.java:24,31` | 코드 확인 |
| chat-flow | 참가 구간·약관·연령 확인, 패턴 목적지 거부 | 브로커와 같은 경로 대조기로 판정 | `M/chat/ws/StompAuthChannelInterceptor.java:210` | `ChatStompAuthInterceptorTest` |
| chat-flow | SEND /app/rooms/{roomId}/send | 앱 목적지 접두 `/app` | `M/chat/config/ChatWebSocketConfig.java:84`, `M/chat/ws/ChatStompController.java:45` | 코드 확인 |
| chat-flow | 전송 한도·길이·차단 확인 | 10초 20건, 2000자, 차단 | `application.yml:278-280`, `M/chat/service/ChatRealtimeService.java:29` | `ChatSendServiceTest` |
| chat-flow | 방별 순번으로 저장 | 방 행 잠금 뒤 다음 seq 발급 | `M/chat/service/ChatMessageService.java:53-55` | `ChatMessageServiceTest` |
| chat-flow | PUBLISH chat:broadcast / 다른 인스턴스로 전달 | Redis 발행/구독 | `M/chat/ws/ChatBroadcastRelay.java:51`, `M/chat/service/ChatRealtimeService.java:65` | `ChatRealtimeServiceTest` |
| chat-flow | MESSAGE /topic/rooms/{roomId} | 받은 봉투를 방 토픽으로 | `M/chat/ws/ChatBroadcastRelay.java:62` | 코드 확인 |
| chat-flow | 거절되면 /user/queue/errors | 보낸 사람 한 명에게만 | `M/chat/ws/ChatStompController.java:31,73` | `ChatStompErrorHandlerTest` |

## user-erd — 스키마

| 그림 | 요소(노드/엣지) | 주장 | 근거 file:line | 확인 방법 |
|---|---|---|---|---|
| user-erd | 테이블 노드 전부 | 마이그레이션이 만드는 테이블 | `src/main/resources/db/migration/V*.sql` 의 `CREATE TABLE` | `grep -i "create table"` |
| user-erd | 화살표 전부(34개) | 외래 키(자식 → 부모)만 그린다 | 각 테이블의 `REFERENCES`(V004·V006·V017·V018·V019·V021·V022·V024·V025·V026·V027·V029) | `grep -i references` |
| user-erd | `recommend_training`·`recommend_edits`·`recommend_cancellations` 에 선이 없음 | `job_id` 로 이어지지만 외래 키가 없다 | `V012__recommend_training_signals.sql`(training·edits), `V023__recommend_cancellation.sql` | 마이그레이션 확인 |
| user-erd | 초기 설계의 미사용 테이블 묶음 | 코드가 읽거나 쓰지 않는다 | `V004__init_user_service.sql:77-131`, `git grep` 로 main·test 참조 0 | `git grep -w friends` 등 |
