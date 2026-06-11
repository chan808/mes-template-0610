# Architecture

## Services

| Service | Lang | Port | Role |
|---|---|---|---|
| agolive-api | Kotlin/Spring | 8080 | 인증, CRUD, 내부 토큰 검증 |
| agolive-realtime | Go | 8081 | WebSocket, 위치 동기화, 브로드캐스트 |
| agolive-agent | Python/FastAPI | 8082 | AI 에이전트 세션 관리, Claude API 스트리밍 |

내부 통신:
- `POST /internal/auth/verify` (Go → Spring, 외부 차단)
- `POST /internal/agent/sessions` (Go → Agent, 에이전트 소환)
- `POST /internal/agent/sessions/{id}/message` (Go → Agent, SSE 스트리밍)
- `DELETE /internal/agent/sessions/{id}` (Go → Agent, 에이전트 퇴장)
- `GET /internal/rooms/{id}/messages/context` (Agent → Spring, 최근 채팅 컨텍스트)

---

## Schema

### PostgreSQL

```sql
avatar_templates (id, name, image_url, is_active)

users (id, email, password_hash[nullable], nickname[nullable], avatar_id→avatar_templates[nullable],
       provider[nullable], provider_id[nullable], role[default USER],
       email_verified[default false], token_version[default 0], withdrawn_at[nullable],
       created_at, updated_at)

rooms (id, invite_token[uuid, unique],
       name, owner_id→users, is_private, max_capacity[default 10],
       status[active|closed], deleted_at[nullable], created_at, updated_at)

messages (id, room_id→rooms, user_id→users[nullable=system/agent], content,
          type[chat|system|agent], agent_nickname[nullable, type=agent 표시용], created_at)

-- indexes
CREATE INDEX idx_messages_room_id_id ON messages(room_id, id DESC);
CREATE INDEX idx_rooms_invite_token ON rooms(invite_token);
```

### Redis

| Key | Type | TTL | 용도 |
|---|---|---|---|
| `presence:{roomId}:{userId}` | String (JSON) | 30s | 타일 위치 {x,y,dir,nickname,avatarId} |
| `room:members:{roomId}` | Set | - | 현재 접속자 |
| `RT:{sid}` | String (JSON) | 7d | Refresh Token 세션 |
| `USER_SESSIONS:{userId}` | Set | 30d | 사용자별 세션 SID 인덱스 |
| `ATV:{userId}` | String | 1h | Access Token Version 캐시 |
| `LOCK:REISSUE:{sid}` | String | 3s | Reissue 동시 요청 방지 락 |
| `RESET:{token}` | String | 30m | 비밀번호 재설정 토큰 |
| `EMAIL_VERIFY:{token}` | String | 24h | 이메일 인증 토큰 → userId |
| `EMAIL_VERIFY_USER:{userId}` | String | 24h | userId → 인증 토큰 (중복 발송 방지 역조회) |
| `OAUTH_CODE:{code}` | String | 60s | OAuth one-time code → AT (URL 직접 노출 방지) |

---

## Module Structure (Spring Modulith)

| Module   | 책임 | 상태 |
|----------|------|------|
| auth     | 인증/인가, JWT 발급·검증, OAuth2, RT 세션 관리, tokenVersion 기반 AT 무효화 | 구현 완료 |
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
POST   /api/v1/users                       -- 회원가입 (이메일/비밀번호)
POST   /api/auth/login
POST   /api/auth/logout
POST   /api/auth/reissue
POST   /api/auth/verify-email
POST   /api/auth/verify-email/resend
POST   /api/auth/password-reset/request
POST   /api/auth/password-reset/confirm
GET    /login/oauth2/code/{provider}       -- OAuth2 콜백 (Spring Security 처리)
GET    /api/auth/oauth2/token?code={code}  -- OAuth one-time code → AT 교환

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
POST /internal/auth/verify                      -- JWT 검증 + 유저 정보 반환 (Authorization 헤더 필요)
GET  /internal/rooms/{roomId}                   -- 방 정보 조회 (maxCapacity, status)
POST /internal/rooms/{roomId}/messages          -- 메시지 저장
GET  /internal/rooms/{roomId}/messages/context  -- 최근 채팅 컨텍스트 (에이전트용)
```

### agolive-agent 내부 API

```
POST   /internal/agent/sessions                       -- 에이전트 소환 (roomId, role)
POST   /internal/agent/sessions/{agentId}/message     -- SSE: 사용자 메시지 → 에이전트 응답 스트리밍
DELETE /internal/agent/sessions/{agentId}             -- 에이전트 퇴장/취소
GET    /health
```

모든 내부 API는 `X-Internal-Secret` 헤더로 인증.

---

## WebSocket Events

```
client → server
  move          {x, y, dir}                  -- 타일 좌표 + 방향 (up|down|left|right)
  chat          {content}
  ping          {}
  whisper       {targetUserId, content}      -- 1:1 귓속말 (ADR-0002)
  summon_agent  {role: "helper"|"summarizer"|"researcher"|"critic"|"orchestrator"}
  dismiss_agent {agentId}
  agent_input   {agentId, response}          -- HitL 사용자 응답

server → client
  presence          {userId, x, y, dir, nickname, avatarId}
  chat              {messageId, userId, content, createdAt}
  join              {userId, nickname}
  leave             {userId}
  pong              {}
  whisper           {fromUserId, toUserId, nickname, content, createdAt}  -- 발신자+수신자에게만
  error             {code, message}
  agent_joined      {agentId, role, nickname, x, y}   -- 신규 입장자에게도 활성 에이전트 수만큼 재전송
  agent_left        {agentId}
  agent_message     {agentId, content, done}
  agent_needs_input {agentId, toolUseId, prompt, options}
  agent_thinking    {agentId, step}
  agent_file        {agentId, filename, url, mimeType}
```

채팅 → 에이전트 라우팅: 메시지에 등록된 에이전트 닉네임과 일치하는 `@닉네임`이 있으면
해당 에이전트에게만, 없으면(무관한 `@` 포함) 모든 에이전트에게 전달한다.
에이전트 아바타 더블클릭은 `@닉네임` 자동 삽입 단축키 (프론트 전용, ADR-0002).

귓속말(whisper): DB 미저장 휘발성, 발신자+수신자에게만 전송 (Pub/Sub 미경유 hub 직접 전송).
content 최대 500자(rune). 대상 부재 시 `error {code: WHISPER_TARGET_NOT_FOUND}`,
자기 자신 대상 시 `error {code: WHISPER_SELF}`. 상세는 `docs/adr/0002-whisper-ephemeral.md`.

---

## Movement (타일 좌표계)

좌표는 픽셀이 아닌 타일 단위다. 맵 30×20 타일, 타일 1칸 = 40px (캔버스 1200×800).
관련 코드: `realtime/game/movement.go`, `frontend/src/features/room/lib/tile.ts` — 상수를 양쪽에서 일치시킬 것.

- 스폰 위치: (15, 10). 클라이언트는 입장 시 서버가 브로드캐스트하는 자신의 presence를 권위 위치로 채택
- 이동 단위: 4방향(상하좌우) 한 칸. 클라이언트는 한 칸을 160ms에 보간 렌더링하고 칸 확정 시점에 move 1회 전송
- 서버 검증 (Go가 권위, 게임 모드 판정의 기반):
  - 범위: 0 ≤ x < 30, 0 ≤ y < 20, 정수만 허용
  - 인접성: 직전 위치에서 상하좌우 1칸만 허용 (대각선·순간이동 거부)
  - 속도: 토큰 버킷 (회복 주기 140ms, 버스트 3) — 과속·치팅 차단
  - 검증 실패 시 해당 클라이언트에게만 서버 권위 위치 presence를 회신 → 클라이언트가 두 칸 이상 어긋나면 스냅 보정
- 캐릭터끼리는 충돌하지 않음 (통로 막힘 방지). 지형 충돌은 가구에서 파생되며 클라이언트 전용 (`lib/furniture.ts`의 `buildCollision`)
  - 현재 서버는 범위·인접성·속도만 검증. 맵 데이터를 서버로 옮기는 시점에 가구 충돌도 서버 검증에 포함할 것 (벽 통과 치팅 방지)

### 공간 렌더링 (PixiJS)

월드(바닥·가구·아바타)는 PixiJS(WebGL) 캔버스가, UI(채팅·다이얼로그·오버레이)는 React/DOM이 담당한다.
도입 이유: 가구 배치·맵 크기 가변·다수 엔티티 렌더링은 DOM 방식(요소별 CSS transition)으로는 코너 가로지름/스터터와 노드 수 한계가 있다.
관련 코드: `frontend/src/features/room/components/SpaceCanvas.tsx`, `lib/tileWalker.ts`, `lib/camera.ts`, `lib/furniture.ts`, `lib/maps.ts`

- 원격 이동 보간: presence 이벤트를 `TileWalker` 큐에 쌓아 타일당 160ms로 순서 재생.
  코너를 대각선으로 가로지르지 않으며, 큐가 3칸 이상 밀리면 배속으로 따라잡고 비인접 좌표(보정·순간이동)는 즉시 스냅
- 카메라: 내 아바타를 뷰포트 중앙에 추적, 월드 가장자리에서 클램프. 월드가 뷰포트보다 작으면 중앙 정렬 (`cameraOffset`)
- 가구 모델: footprint(타일 단위 w×h) + `passable` 플래그. 통과 가구(러그)는 아바타 아래 레이어,
  차단 가구는 아바타와 같은 레이어에서 y 기준 zIndex 정렬(아바타가 가구 뒤로 가면 가려짐)
- 맵 크기: `lib/maps.ts`의 `resolveMapSpec()`이 단일 분기점 — 구독 등급별 맵 크기는 여기서 확장.
  단, 서버가 같은 범위로 검증하므로 방 데이터에 맵 크기를 싣고 `realtime/game/movement.go`도 함께 변경해야 함
- 가구 데이터: 기본 레이아웃은 `lib/maps.ts`의 `SEED_FURNITURE` (placedBy null). 서버 맵 데이터 도입 시 입장 응답으로 대체
- 에이전트 행동 확장 지점: 아바타는 스토어 상태를 큐로 재생만 하므로, 추후 AI의 대화형 지시(이동·이펙트 등)는
  서버 이벤트 → presence/agent 스토어 갱신만으로 렌더러 수정 없이 동작한다

### 가구 편집 (클라이언트 구현, 서버 연동 예정)

"가구 편집" 토글로 편집 모드 진입(패널 열림 동안만 격자 표시·드래그 가능). 포인터 기반 집기 → 고스트 → 놓기 — HTML5 DnD API 미사용.
관련 코드: `stores/furnitureStore.ts`(상태·권한), `stores/editorStore.ts`(드래그 세션), `components/FurniturePanel.tsx`, `components/FurnitureDropZone.tsx`

- 배치: 패널에서 가구를 끌어 맵에 놓기. 고스트 footprint에 초록(가능)/빨강(불가) 표시
- 이동: 배치된 가구를 끌어 옮기기. 회전: 드래그 중 `R` (90도 단위, 홀수 회전 시 footprint w↔h)
- 취소/삭제: 상단 중앙 드롭존 — 신규는 ✕취소, 기존 가구는 삭제. 패널 위 드롭·ESC·우클릭은 취소
- 배치 규칙(`canPlace`): 맵 범위 내 + 차단 가구끼리 겹침 금지 + 차단 가구의 스폰 타일 점유 금지. 통과 가구(러그)는 겹침 자유
- 권한(`canEditFurniture`): 방장은 전체, 멤버는 본인이 배치한 가구만, 기본 가구(placedBy null)는 방장만
- 충돌 연동: 충돌맵은 가구 스토어에서 파생(`useRoomMap`) — 배치/이동/삭제가 이동 가능 영역에 즉시 반영
- 현재 한계: 클라이언트 로컬 상태 — 새로고침 시 SEED로 초기화되고 다른 접속자에게 공유되지 않음. 방 전환/퇴장 시 스토어 리셋

**서버 연동 계획 (미구현, 다음 작업)**

스토어 액션과 1:1 대응으로 설계되어 있어 액션 내부에 send 추가 + 수신 핸들러만 붙이면 된다.

1. WS 이벤트 (클라→서버): `place_furniture {kind, x, y, rotation}` / `move_furniture {id, x, y, rotation}` / `remove_furniture {id}`
2. 서버 검증 (Go, 클라이언트 `canPlace`/`canEditFurniture`와 동일 규칙): 방 멤버십 → 권한(방장 전체/멤버 본인 것) → 범위 →
   차단 가구 겹침 → 스폰 타일 → rate limit. kind는 서버 카탈로그에서 조회(크기·passable을 클라 입력으로 받지 않음)
3. 브로드캐스트 (서버→클라): `furniture_placed {furniture}` / `furniture_moved {id, x, y, rotation}` / `furniture_removed {id}`
   — 수신 시 furnitureStore 갱신(본인 낙관 반영분은 id로 멱등 처리)
4. 영속화: `room_furniture` 테이블 (id, room_id, kind, x, y, rotation, placed_by, created_at) — passable/크기/색은 카탈로그에서 파생.
   입장 시(REST 또는 WS 초기 이벤트) 가구 목록을 내려 `SEED_FURNITURE`를 대체
5. 구독 등급 연계: 방(=방장)의 플랜에 따라 가구 개수 상한·맵 크기(`resolveMapSpec`) 서버 검증 추가

---

## Auth Flow

1. 로그인 → Spring 발급: Access(30m) + Refresh(7d, Redis `RT:{sid}` 저장)
2. WS 연결 → Go: JWT 로컬 서명 검증 → `/internal/auth/verify` (tokenVersion + 유저 정보 확인)
3. 로그아웃 → Spring: Redis `RT:{sid}` 삭제 → 신규 AT 발급 불가
   - 기존 AT는 만료 시(최대 30분)까지 stateless하게 유효 (JWT 구조상 즉시 무효화 불가)
   - 비밀번호 변경 / 회원 탈퇴 시에만 tokenVersion 증가 → 발급된 모든 AT 즉시 무효화

JWT claims: `sub(userId), role, tokenVersion, iat, exp`

---

## Business Rules

- 정원 초과 시 신규 입장 거부 (기존 접속자 유지)
  - 정원 검사·멤버 등록은 Redis Lua 스크립트로 원자 처리 (동시 입장 race 방지)
  - 같은 유저의 다중 탭은 1명으로 계산하며, 마지막 연결 종료 시에만 멤버십·presence 제거
- 최대 인원 수정 시 현재 접속자 수 미만으로 설정 불가
- 초대 토큰 재생성 시 기존 토큰 즉시 무효화 (경고 필요)
- 방 삭제: 소프트 삭제 → 30일 후 배치 물리 삭제 (`@Scheduled`)
- presence 30s TTL: 클라이언트 20s마다 ping으로 갱신
- 귓속말은 DB 미저장 (휘발성) — 같은 방 접속자에게만 가능, 새로고침 시 소실 (ADR-0002)
- 이동은 서버 권위: 타일 범위·인접성·속도 검증을 통과한 move만 반영 (Movement 섹션 참고)

---

## Error Response

RFC 7807 ProblemDetail 형식. 프론트엔드는 `title`(에러 코드)과 `detail`(메시지)을 읽는다.

```json
{
  "status": 409,
  "title": "ROOM_FULL",
  "detail": "정원이 초과되었습니다.",
  "instance": "/api/v1/rooms/1"
}
```

| title | HTTP | 설명 |
|-------|------|------|
| UNAUTHENTICATED | 401 | 인증 실패 또는 토큰 없음 |
| TOKEN_EXPIRED | 401 | 액세스 토큰 만료 |
| TOKEN_INVALID | 401 | 유효하지 않은 토큰 |
| ACCESS_DENIED | 403 | 권한 없음 |
| ROOM_NOT_FOUND | 404 | 존재하지 않는 방 |
| ROOM_FULL | 409 | 정원 초과 |
| ROOM_CLOSED | 409 | 이미 닫힌 방 |
| INVALID_INVITE_TOKEN | 400 | 유효하지 않은 초대 토큰 |
| CAPACITY_BELOW_CURRENT | 400 | 현재 접속자 수보다 적은 정원으로 변경 시도 |
| EMAIL_ALREADY_EXISTS | 409 | 이미 가입된 이메일 |
| TOO_MANY_REQUESTS | 429 | 요청 횟수 초과, `Retry-After` 헤더 포함 |

---

## Infra

### Docker Compose
- postgres:16-alpine / redis:7-alpine
- agolive-agent (Python/FastAPI, stateless — B/G 불필요)
- Nginx: `/api/**` → 8080, `/ws/**` → 8081 (WebSocket upgrade), `/internal/**` deny, `/grafana/**` → Grafana
- 내부 전용 포트 8090: Go → Spring 내부 호출 경유 (외부 미노출)

### Monitoring
- Prometheus: api Blue/Green, realtime Blue/Green, node-exporter scrape (15s 간격)
- Grafana: `https://{DOMAIN}/grafana` (서브패스)
- node-exporter: EC2 시스템 메트릭
- Go 커스텀 메트릭: `ws_active_connections_total` (Gauge, 활성 WS 연결 수)

### EKS (추후 전환 시 추가 예정)

---

## Decision Log

| 결정 | 선택 | 이유 |
|---|---|---|
| 실시간 서버 | Go | goroutine 동시성, JVM 대비 메모리 효율 |
| AI 에이전트 서버 | Python/FastAPI 별도 서비스 (agolive-agent) | 에이전트 상태관리/오케스트레이션은 Spring/Go와 역할이 달라 분리. 인메모리 세션, Claude Haiku SSE 스트리밍 |
| WS 라이브러리 | coder/websocket | context 지원, 활발한 유지보수 |
| 내부 통신 | REST → gRPC (2차) | 초기 개발 속도 우선 |
| 메시지 브로커 | Redis Pub/Sub → Kafka (2차) | 1차 규모에 충분 |
| 위치 저장 | Redis only | 고빈도 쓰기, 영속성 불필요 |
| 초대 방식 | UUID 토큰 | 예측 불가, 재생성 가능 |
| 방 삭제 | 소프트 삭제 | 채팅 기록 보존 |

---

## Agent Architecture (Phase 2)

agolive-agent (Python/FastAPI) — 구현 완료

- 방 당 에이전트 1개 (role: helper | summarizer)
- 에이전트 세션 in-memory 관리 (agentId → AgentSession)
- 소환 시 최근 채팅 컨텍스트를 Spring에서 로드하여 system prompt에 반영
- 사용자 메시지마다 Claude Haiku (`claude-haiku-4-5-20251001`)로 SSE 스트리밍 응답
- 대화 히스토리 최대 40턴 유지 (초과 시 오래된 것부터 제거, 짝수 쌍 유지)
- 에이전트 고정 타일 위치: 슬롯 순서대로 (22,5), (17,5), (22,12), (17,12)
- 마지막 클라이언트 퇴장 시 고아 에이전트 자동 정리 (Hub.Leave → cleanupAgent)
- Go에서 SSE 스트리밍 수신 시 별도 `agentC` (timeout 없음) 사용

**에이전트 소환 플로우**
```
client summon_agent → Go → POST /internal/agent/sessions
  → agent_joined 브로드캐스트 (agentId, role, nickname, x, y)
user chat → Go → 메시지 저장/브로드캐스트 →
  POST /internal/agent/sessions/{id}/message (SSE, goroutine)
  → agent_message 청크 브로드캐스트 (done: false → done: true)
client dismiss_agent → Go → DELETE /internal/agent/sessions/{id}
  → agent_left 브로드캐스트
```

---

## Agent Architecture (Phase 3)

agolive-agent (Python/FastAPI) — 구현 완료

**멀티 에이전트**
- 방 당 최대 4개 에이전트 동시 소환 (Go hub의 슬롯 예약으로 정원·위치를 원자적으로 결정)
- 역할: helper | summarizer | researcher | critic | orchestrator
- 동일 역할 중복 소환 시 닉네임에 번호 부여 (예: "AI 도우미 2")
- 등록된 닉네임과 일치하는 `@닉네임` 포함 시 해당 에이전트에게만 전달, 없으면 모든 에이전트
- 신규 입장자에게 현재 에이전트 목록 즉시 전송 (`agent_joined` 반복 전송)
- 에이전트별 개별 퇴장 (`dismiss_agent {agentId}`) — 진행 중 스트림/HitL 대기도 컨텍스트로 함께 취소
- 에이전트 세션 소실(서비스 재시작 등) 감지 시 hub에서 자동 정리 + `agent_left` 브로드캐스트
- 에이전트 응답 전문은 완료 시 `type=agent` 메시지로 DB 저장 (새로고침 후에도 히스토리 유지)

**Tool Use 루프 (SSE 기반)**
```
Python: while True:
  Claude API stream → text 청크 yield (content, done: false)
  stop_reason == tool_use → {type:"tool_use", toolName, toolInput, toolUseId} yield
  _tool_event.wait(timeout=180s, keepalive 5s) → Go가 /tool_result 주입 → 루프 재진행
  stop_reason == end_turn → {done: true} yield
```

타임아웃 계층: Python tool_result 대기(180s) > Go HitL 사용자 응답 대기(120s).
Python 대기가 더 길어야 타임아웃 직전의 사용자 응답이 유실되지 않는다.
히스토리 절단(40개)은 tool_use/tool_result 쌍이 깨지지 않는 경계에서 수행한다.

동시성 규칙:
- `disable_parallel_tool_use`로 턴당 tool_use 1개만 허용 (Go가 결과를 블록별로 주입하므로)
- 같은 세션의 메시지 스트림은 세션 락으로 직렬화 (연속 채팅 시 history 동시 변형 방지)

**Human-in-the-loop (request_human_input 툴)**
```
agent → tool_use: request_human_input
  → Go: agent_needs_input 브로드캐스트 {agentId, toolUseId, prompt, options}
  → Frontend: HumanInLoopDialog 표시
  → user 선택/입력 → WS: agent_input {agentId, response}
  → Go: AgentState.HumanInputCh ← response
  → Go: POST /tool_result → Python 재개
  timeout 120s: "(사용자 응답 없음)" 자동 주입
```

**Orchestrator (delegate_to_worker 툴)**
```
orchestrator → tool_use: delegate_to_worker {role, task}
  → Go: agent_thinking 브로드캐스트
  → Go: 워커 소환 → agent_joined 브로드캐스트
  → Go: streamWorkerAndCollect (워커 SSE 브로드캐스트 + 텍스트 누적)
  → Go: 워커 정리 → agent_left 브로드캐스트
  → Go: POST /tool_result {workerResponse}
  → orchestrator 최종 합성 응답
```

**S3 파일 결과물 (create_document 툴)**
```
agent → tool_use: create_document {filename, content, mime_type}
  → Go: POST /internal/files → Python: aioboto3 S3 업로드 → presigned URL (7일)
  → Go: agent_file 브로드캐스트
  → Frontend: 파일 카드 (다운로드 링크)
  → Go: POST /tool_result {url}
```

**agolive-agent 내부 API (Phase 3 추가)**
```
POST /internal/agent/sessions/{id}/tool_result  -- tool 결과 주입 (HitL 포함)
POST /internal/files                            -- S3 업로드 (create_document 전용)
```
