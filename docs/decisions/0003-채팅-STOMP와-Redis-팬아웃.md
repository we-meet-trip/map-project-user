# 0003. 실시간 채팅을 STOMP 와 Redis 발행/구독으로 만든다

## 맥락
일정마다 대화방이 필요하고, BFF 가 여러 인스턴스로 떠도 같은 방 사람에게 메시지가 가야 한다.

## 선택지
1. 외부 채팅 서비스
2. STOMP over WebSocket + 인메모리 브로커 + Redis 발행/구독 팬아웃
3. STOMP 브로커 릴레이(RabbitMQ 등) 추가 운영

## 결정
2번. 메시지는 PostgreSQL 에 방별 순번으로 저장하고, 인스턴스 사이에는 Redis 채널 `chat:broadcast` 로 퍼뜨린다. 접속·전송·구독마다 JWT·참가 구간·약관·연령 조건을 다시 본다.

## 결과
추가 인프라 없이 Redis 하나로 수평 확장된다. 구독 목적지 인가를 직접 해야 하므로, 판정은 브로커와 같은 경로 대조기로 한다([troubleshooting.md](../troubleshooting.md) T-CHAT-01). 하트비트는 브로커 스케줄러를 붙여야 실제로 협상된다(T-CHAT-02).
