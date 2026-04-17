# Architecture

## Services

| Service | Lang | Port | Role |
|---|---|---|---|
| agolive-api | Kotlin/Spring | 8080 | 인증, CRUD, 내부 토큰 검증 |
| agolive-realtime | Go | 8081 | WebSocket, 위치 동기화, 브로드캐스트 |

내부 통신: `POST /internal/auth/verify` (Go → Spring, 외부 차단)

---

## Schema

### PostgreSQL

```sql
avatar_templates (id, name, image_url, is_active)

users (id, email, password_hash[nullable], nickname[nullable], avatar_id→avatar_templates[nullable],
       provider[nullable], provider_id[nullable], role[default USER],
       email_verified[default false], token_version[default 0], withdrawn_at[nullable],
       created_at, updated_at)

rooms (id, invite_token[uuid, unique], invite_token_expires_at[nullable],
       name, owner_id→users, is_private, max_capacity[default 10],
       status[active|closed], deleted_at[nullable], created_at, updated_at)

messages (id, room_id→rooms, user_id→users[nullable=system], content,
          type[chat|system], created_at)

-- indexes
CREATE INDEX idx_messages_room_id_id ON messages(room_id, id DESC);
CREATE INDEX idx_rooms_invite_token ON rooms(invite_token);
```

### Redis

| Key | Type | TTL | 용도 |
|---|---|---|---|
| `presence:{roomId}:{userId}` | String (JSON) | 30s | 위치 {x,y,nickname,avatarId} |
| `room:members:{roomId}` | Set | - | 현재 접속자 |
| `RT:{sid}` | String (JSON) | 7d | Refresh Token 세션 |
| `USER_SESSIONS:{userId}` | Set | 30d | 사용자별 세션 SID 인덱스 |
| `ATV:{userId}` | String | 1h | Access Token Version 캐시 |
| `LOCK:REISSUE:{sid}` | String | 3s | Reissue 동시 요청 방지 락 |
| `RESET:{token}` | String | 30m | 비밀번호 재설정 토큰 |
| `EMAIL_VERIFY_USER:{token}` | String | - | 이메일 인증 토큰 |

---

## Module Structure (Spring Modulith)

| Module   | 책임 | 상태 |
|----------|------|------|
| auth     | 인증/인가, JWT 발급·검증, OAuth2, 토큰 블랙리스트 | 구현 완료 |
| user     | 사용자 프로필 조회·수정, 아바타 목록 | 구현 완료 |
| room     | 방 생성·수정·삭제, 초대 토큰 관리, 소프트 삭제 스케줄러 | 구현 완료 |
| message  | 채팅 기록 저장·커서 페이지 조회 | 구현 완료 |
| internal | Go 서버 전용 내부 API (/internal/**) | 구현 완료 |

### 모듈 간 의존 규칙

타 모듈의 내부 패키지(infrastructure 등) 직접 참조 금지. 공개 API(`{module}/api/` 패키지)를 통해서만 접근한다.

```
auth   → user :: api, user :: events
message → room :: api
```

---

## API

### agolive-api

```
POST   /api/users                          -- 회원가입 (이메일/비밀번호)
POST   /api/auth/login
POST   /api/auth/logout
POST   /api/auth/reissue
POST   /api/auth/verify-email
POST   /api/auth/verify-email/resend
POST   /api/auth/password-reset/request
POST   /api/auth/password-reset/confirm
GET    /login/oauth2/code/{provider}       -- OAuth2 콜백

GET    /api/v1/users/me
PATCH  /api/v1/users/me                   -- nickname, avatarId
PATCH  /api/v1/users/me/password
DELETE /api/v1/users/me
GET    /api/v1/avatars

POST   /api/v1/rooms
GET    /api/v1/rooms
GET    /api/v1/rooms/{id}
PATCH  /api/v1/rooms/{id}
DELETE /api/v1/rooms/{id}
POST   /api/v1/rooms/{id}/invite        -- 초대 토큰 재생성
GET    /api/v1/rooms/join/{token}       -- 초대 링크로 방 정보 조회

GET    /api/v1/rooms/{id}/messages?before={id}&limit=50

POST   /internal/auth/verify            -- Go 전용, Nginx 외부 차단
```

### agolive-realtime

```
WS  /ws/rooms/{roomId}?token={jwt}
GET /health
```

### agolive-realtime 내부 API (Spring 호출)

```
POST /internal/auth/verify              -- JWT 검증 + 유저 정보 반환 (Authorization 헤더 필요)
GET  /internal/rooms/{roomId}           -- 방 정보 조회 (maxCapacity, status)
POST /internal/rooms/{roomId}/messages  -- 메시지 저장
```

---

## WebSocket Events

```
client → server
  move    {x, y}
  chat    {content}
  ping    {}

server → client
  presence  {userId, x, y, nickname, avatarId}
  chat      {messageId, userId, content, createdAt}
  join      {userId, nickname}
  leave     {userId}
  pong      {}
  error     {code, message}
```

---

## Auth Flow

1. 로그인 → Spring 발급: Access(15m) + Refresh(7d, Redis 저장)
2. WS 연결 → Go: JWT 로컬 검증 → Redis blacklist 확인 → `/internal/auth/verify`
3. 로그아웃 → Spring: Redis `blacklist:token:{jti}` 기록

JWT claims: `sub(userId), nickname, avatarId, jti, iat, exp`

---

## Business Rules

- 정원 초과 시 신규 입장 거부 (기존 접속자 유지)
- 최대 인원 수정 시 현재 접속자 수 미만으로 설정 불가
- 초대 토큰 재생성 시 기존 토큰 즉시 무효화 (경고 필요)
- 방 삭제: 소프트 삭제 → 30일 후 배치 물리 삭제 (`@Scheduled`)
- presence 30s TTL: 클라이언트 20s마다 ping으로 갱신

---

## Error Response

```json
{ "code": "ROOM_FULL", "message": "정원이 초과되었습니다." }
```

| code | HTTP | 설명 |
|------|------|------|
| UNAUTHORIZED | 401 | 인증 실패 또는 토큰 만료 |
| FORBIDDEN | 403 | 권한 없음 |
| ROOM_NOT_FOUND | 404 | 존재하지 않는 방 |
| ROOM_FULL | 409 | 정원 초과 |
| ROOM_CLOSED | 409 | 이미 닫힌 방 |
| INVALID_INVITE_TOKEN | 400 | 유효하지 않은 초대 토큰 |
| CAPACITY_BELOW_CURRENT | 400 | 현재 접속자 수보다 적은 정원으로 변경 시도 |
| DUPLICATE_EMAIL | 409 | 이미 가입된 이메일 |

---

## Infra

### Docker Compose (1차)
- postgres:16-alpine / redis:7-alpine
- Nginx: `/api/**` → 8080, `/ws/**` → 8081 (WebSocket upgrade), `/internal/**` deny

### EKS (2차 전환 시 추가 예정)

---

## Decision Log

| 결정 | 선택 | 이유 |
|---|---|---|
| 실시간 서버 | Go | goroutine 동시성, JVM 대비 메모리 효율 |
| AI 에이전트 서버 | 2차에 별도 서비스로 추가 예정 | 에이전트 상태관리/오케스트레이션은 Spring/Go와 역할이 달라 분리 |
| WS 라이브러리 | coder/websocket | context 지원, 활발한 유지보수 |
| 내부 통신 | REST → gRPC (2차) | 초기 개발 속도 우선 |
| 메시지 브로커 | Redis Pub/Sub → Kafka (2차) | 1차 규모에 충분 |
| 위치 저장 | Redis only | 고빈도 쓰기, 영속성 불필요 |
| 초대 방식 | UUID 토큰 | 예측 불가, 재생성 가능 |
| 방 삭제 | 소프트 삭제 | 채팅 기록 보존 |

---

## Agent Architecture (Phase 2+)

2차부터 추가될 에이전트 서버 구조 (설계 예정)

agolive-agent (Python/FastAPI 예정)
- 단일/멀티 에이전트 실행 및 상태 관리
- Claude API tool use 기반 도구 실행
- Human-in-the-loop 인터럽트 처리
- 에이전트 응답 스트리밍 → Go 실시간 서버 경유 → 클라이언트

에이전트 상태: idle / running / paused / done
파일 생성 결과물은 S3 저장 후 다운로드 URL 제공
