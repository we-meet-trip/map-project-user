# 구조 (map-project-user)

map-project-user 는 앱이 부르는 유일한 백엔드(BFF)다. 인증·일정·채팅·동의는 직접 갖고, 추천은 agent 에, 외부 데이터(날씨·장소·경로·이동수단)는 hub 에 맡긴다. 그림은 [diagrams](diagrams/) 에 있고, 그림의 근거는 [diagrams/CLAIMS.md](diagrams/CLAIMS.md) 에 있다.

## 처리 순서

### 추천(비동기)
1. 앱이 `POST /api/v1/recommend` 로 지역·기간·테마·이동수단·예산을 보낸다.
2. user 는 같은 조건의 결과가 재사용 캐시에 있으면 사본을 이 작업의 `job_id` 로 바꿔 돌려준다. 같은 조건이 이미 돌고 있으면 그 실행에 붙는다(agent 를 다시 부르지 않는다).
3. 아니면 PostgreSQL `recommend_jobs` 에 접수를 기록하고 agent `POST /v1/recommend` 로 넘긴다. 고른 장소가 있으면 봉투(`loc`)로 싼다.
4. agent 는 결과를 Redis Streams `agent:jobs:done` 에 봉투로 싣는다. user 의 소비자는 봉투를 열어 PostgreSQL 에 완료를 기록한 뒤 ack 하고, Redis 초안과 재사용 캐시를 채운다.
5. 앱은 `GET /api/v1/recommend/{jobId}` 로 조회한다. 준비 전에는 202 와 `Retry-After: 3`, 끝나면 200 이다. Redis 초안이 사라져도 PostgreSQL 기록으로 답한다.

### trip(동기)
`POST /api/v1/trip/generate` 는 위 1~4 를 하되, 요청 하나가 결과까지 기다린다(초안을 0.7초 간격으로 확인, 상한 `USER_TRIP_POLL_TIMEOUT_SECONDS` 기본 110초). 결과를 정류지·이동 카드로 접고, hub 날씨와 도로 경로(구간 20개씩 묶어 요청)를 붙여 200 으로 준다. 대기 상한은 앱 > 앞단 프록시 > 이 값 > 통상 완료 시간 순서여야 한다 — 앞단이 먼저 끊으면 서버가 준비한 시간초과 사유가 앱에 닿지 않는다.

### 일정과 날씨 감시
- 일정을 저장할 때 그 시점의 예보를 기준선(`schedules.weather_baseline`)으로 굳힌다.
- `ScheduleWeatherWatcher` 가 30분마다(`USER_WEATHER_WATCH_INTERVAL_MS`) 끝나지 않은 일정의 예보를 다시 받아, 강수확률 50% 경계나 하늘 상태가 비·눈으로 바뀌면 `weather_alert` 를 건다.
- 사용자가 알림을 무시하거나 재추천하면 기준선을 지금 예보로 옮긴다. 옮기지 않으면 다음 순회에서 같은 변화를 또 잡는다.
- 종료일이 지난 일정은 감시·알림·재추천하지 않는다.

### 재탐색
`POST /api/v1/recommend/{jobId}/research` 는 이미 본 장소를 `exclude` 에 담아 `stage=reexplore` 로 다시 추천한다. 계정당 하루 3회(KST 자정 초기화, Redis 카운터). 날씨 때문에 다시 짜는 재추천(`/replan`)은 이 한도를 쓰지 않는다.

### 채팅
STOMP over WebSocket(`/ws/chat`). 메시지는 PostgreSQL 에 방별 순번으로 저장하고, 인스턴스 사이에는 Redis 발행/구독(`chat:broadcast`)으로 퍼뜨린다. 접속·전송·구독 때마다 JWT, 참가 구간, 서비스 약관·18세 이상 조건을 다시 본다. 나간 사람은 나간 뒤의 메시지를 받거나 조회할 수 없다.

### 리뷰 요약
장소를 누르면 hub 블로그 검색 결과를 agent 가 두 줄로 요약한다. 앱이 명시 동의(`consent:true`)와 요청 ID 를 보낼 때만 새로 만들고, 그 밖에는 캐시만 읽는다. 일정 저장 직후 미리 만드는 선작성은 기본 꺼짐이다(`USER_REVIEWS_PREWARM_ENABLED`) — agent 의 요약 기능을 켤 때 함께 켠다.

## 모듈

| 패키지 | 역할 |
|---|---|
| `map.project.user.domain.auth`, `…domain.user` | 이메일·카카오·Apple 로그인, JWT 발급·회전·폐기, 회원 정보·탈퇴 |
| `…global.config`·`…global.security`·`…global.jwt`·`…global.ratelimit` | 보안 체인, 인증 경로 요청 한도, 기동 검사(인증 시행 시 키·CORS·토큰 확인, 서빙 DB 역할 검사) |
| `…global.crypto` | 저장 본문 암호화(`PayloadCipher`, AES-GCM, 키 여러 개로 교체 가능), 서비스 사이 좌표 봉투(`LocationSeal`) |
| `…recommend` | 추천 접수·조회·편집·재탐색, 재사용 캐시, 스트림 소비자, 학습 신호 기록(기본 꺼짐) |
| `…trip` | 동기 trip facade, agent·hub 응답을 앱 형식으로 바꾸는 규칙(`TripMapping`), 도로 경로 묶음 요청 |
| `…schedule`·`…weather` | 일정 저장·시작·도착 기록, 날씨 기준선과 변화 감시 |
| `…chat` | 채팅방·참가·초대·메시지, STOMP 설정과 인가 |
| `…policy` | 서비스 약관·개인정보·18세 이상 동의, 외부 AI 사용 동의(`trip`·`vision`·`review_summary`) |
| `…places`·`…nearby`·`…transit`·`…mobility` | hub 중계(장소 검색·사진·리뷰·주변 장소·대중교통·자전거·킥보드) |
| `…moderation` | 신고·차단, 운영 콘솔의 신고 처리 |
| `…admin` | 운영 콘솔용 내부 API(회원·추천 작업·DLQ) |
| `…vision` | 비전 서비스의 하루 사용 허가 |
| `map.migration`·`map.bootstrap`·`map.database` | 마이그레이션 진입점, 빈 DB 부트스트랩 진입점, DB 권한 계약(서빙의 컴포넌트 스캔 밖) |
| `map.export` | 학습 자료 내보내기 전용 진입점(서빙과 따로 띄운다) |

## 외부 의존성

| 대상 | 쓰임 | 없을 때 |
|---|---|---|
| PostgreSQL 17 | 계정·일정·추천 작업·채팅·동의(스키마 `user_service`) | 기동하지 않는다 |
| Redis 7 | JWT 폐기 목록, 요청 한도, 스트림, 초안, 재사용 캐시, 채팅 접속 상태 | 스트림 소비자가 구독을 다시 시도한다(워치독 10초). 캐시 경로는 대부분 없는 셈 치고 진행 |
| agent | 추천·리뷰 요약 | 추천 실패 코드(`upstream_unavailable` 등)로 답한다 |
| hub | 날씨·장소·경로·이동수단 | 해당 기능만 실패하거나 비어 나간다(예: 날씨가 없으면 예보 칸이 빈다) |
| 카카오·Apple | 로그인 토큰 확인 | 설정하지 않으면 그 로그인만 꺼진다 |

Redis 논리 DB 는 용도별로 나눈다: 기본 0, JWT 폐기 1, 스트림 2, 카운터·요청 한도 3, 초안 4, 채팅 5, 재사용 캐시 6(`USER_REDIS_DB_*`·`MAP_PROJECT_REDIS_DB_*`).

## 타임아웃과 한도
- agent 60초, hub 5초, hub 길찾기 묶음 20초, trip 대기 110초(초안 확인 0.7초 간격).
- 스트림: 재시도 3회 뒤 DLQ, 회수 대상 최소 대기 60초, 회수 주기 30초, DLQ 보존 2000건, 스트림 전용 명령 시한 6초.
- 재사용 캐시 7일, 작업-캐시 연결 1시간, 같은 조건 실행 대기 10분, 초안 1시간.
- 그 밖의 한도는 [contracts.md](contracts.md) '한도' 를 본다.

## 데이터베이스 준비

서빙은 마이그레이션하지 않는다(`spring.flyway.enabled=false`, 켜면 기동 거부). 빈 데이터베이스는 아래 순서로 세운다. 통합 실행(map-project-infra)의 빠른 시작이 이 순서를 일회성 서비스로 돌린다.

1. 슈퍼유저 psql 로 `scripts/sql/user-database-bootstrap-prepare.sql` — 역할(`map_user_owner`·`map_user_migrator`·`map_user_runtime`, 잠긴 상태)과 일회용 `map_user_bootstrap` 을 만든다.
2. `map.bootstrap.UserBootstrapApplication bootstrap` — `map_user_bootstrap` 으로 V001~V004 만 적용한다. 받는 환경변수는 `USER_BOOTSTRAP_URL·USERNAME·PASSWORD·EXPECTED_DATABASE·MARKER` 뿐이다.
3. 슈퍼유저 psql 로 `…-finalize.sql`, `…-activate.sql` — 표 목록을 확인하고 소유권을 넘긴 뒤, 부트스트랩 역할을 잠그고 서빙·마이그레이션 역할의 비밀번호를 켠다.
4. `map.migration.UserMigrationApplication migrate` — `map_user_migrator` 로 나머지 마이그레이션을 적용한다. 받는 환경변수는 `USER_MIGRATION_URL·USERNAME·PASSWORD` 뿐이고, 서빙·공급자 비밀이 섞이면 시작하지 않는다.
5. 서빙을 `map_user_runtime` 으로 띄운다. 서빙은 자기 권한이 DML 뿐인지 기동 때 확인한다.

- SQL 은 psql 변수(`:'expected_database'`, `:'marker'`, `:'bootstrap_password'`, `:'runtime_password'`, `:'migrator_password'`)로 값을 받고, PostgreSQL 17 이상이 아니면 멈춘다.
- 이미 쓰던 데이터베이스를 역할 분리로 옮길 때는 `scripts/sql/user-database-role-provisioning.sql` 을 쓴다.
- **이 공개본의 V011·V015 는 주석만 고쳤다.** 그 두 파일을 이전 판으로 이미 적용한 데이터베이스는 다음 마이그레이션 전에 체크섬을 한 번 맞춰야 한다(Flyway 는 주석까지 체크섬에 넣는다). 마이그레이션 진입점에는 repair 동작이 없으므로 Flyway 명령줄 도구를 `map_user_migrator` 로 붙여 `-schemas=user_service -initSql="SET ROLE map_user_owner" repair` 로 돌린다. 새로 세우는 데이터베이스는 해당 없다.
- V004 의 `friends`·`share_sessions`·`share_members`·`trips`·`trip_recommendations`·`trip_segments`·`media_objects` 는 **초기 설계의 미사용 테이블**이다. 코드가 읽거나 쓰지 않는다. 기존 데이터베이스와 부트스트랩 검증(V001~V004 해시 고정)을 깨지 않으려고 지우지 않았다.

## 기본 꺼짐 기능
| 기능 | 스위치 | 설명 |
|---|---|---|
| 인가 시행 | `USER_AUTH_ENFORCED=false` | 로컬 실행용 기본값. 외부에 열기 전에 켠다([SECURITY.md](../SECURITY.md)) |
| 학습 수집 | `MAP_PROJECT_TRAINING_CAPTURE_ENABLED=false` | 켜면 추천 후보·편집·주변 장소 클릭·도착 기록을 학습용으로 남긴다. 내보낼 때 사용자 식별자는 소금 섞은 지문으로 바꾼다([data.md](data.md)) |
| 학습 내보내기 | `USER_TRAINING_EXPORT_ENABLED`·`USER_TRAINING_EXPORT_APPROVED` 둘 다 true | 전용 진입점(`map.export.TrainingExportApplication`, `Dockerfile.training-export`)으로 한 번 돌린다. 서빙에는 없다 |
| 리뷰 요약 선작성 | `USER_REVIEWS_PREWARM_ENABLED=false` | agent 요약을 켤 때 함께 켠다 |
| 시험 계정 시더 | `USER_TESTER_SEED_ENABLED=false` | `maptester1~5@example.com` 다섯 계정을 보장한다. 비밀번호는 `USER_TESTER_SEED_PASSWORD` 로만 받는다 |
| 도보 연결선 | `USER_TRANSIT_WALK_ENABLED=false` | **미완성 실험 기능.** 꺼져 있으면 hub 를 부르지 않고 앱은 직선을 그린다 |
| 카카오·Apple 로그인 | `USER_KAKAO_APP_ID` 비움, `USER_APPLE_ENABLED=false` | 자기 콘솔 앱을 설정할 때만 켜진다 |
