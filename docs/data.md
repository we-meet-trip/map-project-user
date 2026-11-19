# 데이터 카드 (map-project-user)

## 담은 데이터
이 저장소에는 외부 데이터셋·실제 사용자 데이터·스크랩한 본문이 없다. 들어 있는 것은 다음뿐이다.

| 종류 | 위치 | 출처·이용조건 |
|---|---|---|
| 데이터베이스 스키마 | `src/main/resources/db/migration/V001`~`V031` | 이 저장소의 코드(MIT) |
| 테스트 픽스처 | `src/test/**` | 합성 값. 메일은 예약 도메인(`example.com`·`example.org`·`*.invalid`), 좌표·주소는 시군 단위의 일반 값, 장소 이름은 공공 명소 이름이나 가상의 이름이다 |
| 시험용 고정 키 | `src/test/resources/application-test.yaml` 등 | 테스트에서만 쓰는 공개 값(반복 바이트). 실제 배포에 쓰면 안 된다 |

공공·개방 자료를 담지 않는다. 외부 데이터(관광지·날씨·장소·경로·리뷰)는 이 서비스가 직접 받지 않고 map-project-hub 가 각 제공자의 약관에 따라 받는다.

## 이 서비스가 만드는 데이터
실행하면 PostgreSQL 스키마 `user_service` 와 Redis 에 아래가 쌓인다. 저장소에는 들어가지 않는다.

| 데이터 | 저장 | 보호 |
|---|---|---|
| 계정(이메일, BCrypt 비밀번호 해시, 닉네임, 생년월일), 소셜 연결 | `users`·`oauth_accounts`·`apple_accounts` | Apple refresh 토큰은 저장 암호화. 카카오 토큰은 저장하지 않는다 |
| 세션 | `refresh_tokens`(SHA-256 해시), Redis JWT 폐기 목록 | |
| 일정·추천 결과 | `schedules`·`recommend_jobs`·`recommend_edits` | 본문(방문지·좌표)은 저장 암호화(`USER_LOCATION_ENC_KEYS`, AES-GCM, 자리마다 다른 AAD) |
| 채팅 | `chat_rooms`·`chat_participants`·`chat_messages` | 탈퇴하면 본인 메시지는 지우고 다른 사람의 메시지는 남긴다 |
| 동의 | `service_policy_acceptances`·`external_ai_consents` | 판 값 `1.0.0` |
| 신고·차단 | `moderation_*`·`user_blocks` | |
| 학습 신호(기본 꺼짐) | `recommend_training`·`recommend_jobs.user_segment`·`nearby_impressions`·`schedule_arrivals` | 아래 '학습 수집' |

스키마 전체와 관계는 [diagrams](diagrams/) 의 ERD 를 본다. V004 의 `friends`·`share_*`·`trips`·`trip_*`·`media_objects` 는 쓰지 않는 초기 설계 테이블이다.

## 학습 수집과 내보내기 (기본 꺼짐)
- 수집: `MAP_PROJECT_TRAINING_CAPTURE_ENABLED=true` 일 때만 추천 후보·순위·선택, 편집 사본, 주변 장소 노출·클릭, 도착 기록을 남긴다. 꺼져 있으면 이 데이터를 만들지 않는다(추천·일정·주변 장소 조회 자체는 같다).
- 동의: 추천은 외부 AI 사용 동의(`trip` 범위)가 있어야 돈다. 학습 수집은 추천 결과에 딸린 기록이라 같은 동의 위에서만 생긴다. 공개본을 운영할 사람은 학습 수집을 켜기 전에 자기 서비스의 개인정보 처리방침에 그 목적과 보관 기간을 적어야 한다.
- 내보내기: `USER_TRAINING_EXPORT_ENABLED=true` 와 `USER_TRAINING_EXPORT_APPROVED=true` 둘 다일 때, 전용 진입점(`map.export.TrainingExportApplication`)이 JSON Lines 파일과 매니페스트(`schema_version` `1.0.0`, SHA-256)를 만든다. 사용자 식별자는 `USER_TRAINING_EXPORT_USER_REF_SALT` 를 섞은 지문으로 바꾸고, 소금이 비면 내보내지 않는다. 시험 계정 도메인(`USER_TRAINING_EXPORT_TEST_EMAIL_DOMAINS`, 기본 `example.com,test.com,map.test`)은 뺀다.
- 내보낸 파일은 다시 만들 수 있는 파생물이다. 자동으로 지우지 않으므로 쓴 뒤 지운다.

## 한계
- 실제 운영 데이터로 학습하거나 검증한 결과는 이 저장소에 없다. 내보내기 형식은 합성 데이터로만 시험했다.
- 비공개 개발본의 데이터베이스·백업·스냅숏과는 호환되지 않는다(이름·판 값·일부 마이그레이션 주석이 다르다). 이전 판으로 V011·V015 를 적용한 데이터베이스는 [architecture.md](architecture.md) 의 체크섬 안내를 본다.
