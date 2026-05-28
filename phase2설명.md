# Phase 2 코드 이해 가이드

Phase 2에서 추가된 것: **AI 에이전트 서비스** + **모니터링 스택**

---

## 전체 흐름 먼저

```
[브라우저]
  │  WS 연결
  ▼
[Go - realtime/handler/ws.go]  ← 모든 WS 이벤트의 허브
  │  summon_agent 이벤트 수신 시 HTTP POST
  ▼
[Python - agolive-agent/routers/internal.py]  ← AI 에이전트 서비스
  │  소환 응답 후, 이후 채팅마다 SSE 스트리밍
  │  소환 시 채팅 컨텍스트 요청 HTTP GET
  ▼
[Spring - InternalMessageController.kt]  ← /internal/** 엔드포인트 추가됨
```

사용자가 `summon_agent`를 보내면:
1. Go가 Python에 "에이전트 만들어줘" 요청
2. Python이 에이전트 세션 생성 후 응답
3. 이후 사용자 채팅이 올 때마다 Go가 Python에 "이 메시지 처리해줘" 요청
4. Python이 Claude API에 스트리밍 요청 → 청크가 오는 대로 Go에 SSE로 전달
5. Go가 각 청크를 WebSocket으로 브로드캐스트

---

## 파일별 설명

### 1. `agolive-agent/config.py`

```python
class Settings(BaseSettings):
    anthropic_api_key: str
    internal_secret: str
    spring_api_url: str = "http://agolive-nginx:8090"
    port: int = 8082
```

pydantic-settings를 쓴다. `BaseSettings`는 .env 파일 또는 환경변수를 자동으로 읽어서 필드에 매핑해준다.
`spring_api_url`의 기본값이 `agolive-nginx:8090`인 이유: Docker Compose 안에서 컨테이너끼리 통신할 때 서비스명을 호스트명으로 쓴다. 8090은 Nginx 내부 전용 포트 (외부에는 열려있지 않음).

---

### 2. `agolive-agent/main.py`

```python
app = FastAPI(docs_url=None, redoc_url=None)
app.include_router(internal.router)
```

FastAPI 앱 진입점. `docs_url=None`으로 Swagger UI 비활성화 (내부 서비스라 불필요).
라우터는 `routers/internal.py` 하나뿐이다.

---

### 3. `agolive-agent/routers/internal.py` ← Phase 2 핵심

**전역 상태**
```python
_sessions: dict[str, "AgentSession"] = {}
_lock = asyncio.Lock()
```
에이전트 세션을 딕셔너리로 메모리에 저장한다. 재시작하면 사라진다 (의도적 설계 — 에이전트는 영속성이 필요없음).
`_lock`은 동시 요청이 왔을 때 같은 방에 에이전트가 두 개 생기는 걸 막기 위한 async 뮤텍스.

**에이전트 역할 설정**
```python
ROLE_CONFIGS = {
    "helper":     { "nickname": "AI 도우미",  "system": "... 100자 이내로 답하세요" },
    "summarizer": { "nickname": "AI 요약자",  "system": "... 150자 이내로 답하세요" },
}
```
역할별로 system prompt가 다르다. 현재는 2가지.

**`POST /internal/agent/sessions` — 에이전트 소환**
```python
async def summon_agent(body: dict, _=Depends(verify_internal)):
    # 1. 같은 방에 이미 에이전트 있으면 409
    # 2. 최근 채팅 20개 가져와서 system prompt 뒤에 붙임
    # 3. AgentSession 생성 후 _sessions에 저장
    # 4. agentId, 위치(x:900, y:200), nickname, role 반환
```
소환 시 `_fetch_recent_messages(room_id)`로 Spring에서 최근 대화를 가져온다.
이걸 system prompt에 이어붙여서 "내가 들어오기 전에 무슨 얘기를 했는지" 에이전트가 알게 된다.

**`POST /internal/agent/sessions/{agent_id}/message` — 메시지 처리 (SSE)**
```python
async def send_message(agent_id: str, body: dict, _=Depends(verify_internal)):
    user_content = f"유저 {body['userId']}: {body['content']}"
    session.history.append({"role": "user", "content": user_content})
    if len(session.history) > MAX_HISTORY:  # 40턴
        session.history = session.history[-MAX_HISTORY:]
    return StreamingResponse(_stream_response(session), media_type="text/event-stream")
```
`StreamingResponse`를 반환한다. 즉, 이 HTTP 요청은 Claude가 응답을 다 생성할 때까지 연결이 끊기지 않고 청크를 계속 보낸다.

**`_stream_response` — 실제 Claude API 스트리밍**
```python
async def _stream_response(session):
    async with client.messages.stream(...) as stream:
        async for text in stream.text_stream:
            if session.cancelled:
                break
            yield f"data: {json.dumps({'content': text, 'done': False})}\n\n"
    yield f"data: {json.dumps({'content': '', 'done': True})}\n\n"
```
SSE 형식: `data: {...}\n\n`. 각 청크마다 `done: false`로 보내다가, 완료 시 `done: true` 한 번 더.
`session.cancelled` 체크: dismiss_agent 요청이 오면 `session.cancelled = True`가 되고, 다음 청크에서 루프를 탈출한다.

**`DELETE /internal/agent/sessions/{agent_id}` — 에이전트 퇴장**
```python
async def dismiss_agent(agent_id: str, _=Depends(verify_internal)):
    session = _sessions.pop(agent_id, None)
    if session:
        session.cancelled = True
```
`_sessions`에서 제거 + `cancelled = True` 세팅. 스트리밍 중이라면 다음 청크에서 중단된다.

**`verify_internal` — 내부 인증**
```python
def verify_internal(x_internal_secret: str = Header(...)):
    if x_internal_secret != settings.internal_secret:
        raise HTTPException(status_code=401)
```
`X-Internal-Secret` 헤더 검증. Go와 같은 시크릿 값을 공유한다 (`.env`의 `INTERNAL_SECRET`).

---

### 4. `backend/src/main/.../internal/presentation/InternalMessageController.kt`

Phase 2에서 Spring에 추가된 내부 API. Go와 Python만 호출한다 (Nginx가 외부 차단).

```kotlin
@PostMapping
fun save(roomId, request): ResponseEntity  // Go가 채팅 저장 시 호출
    → messageApi.save(roomId, userId, content, type)

@GetMapping("/context")
fun getContext(roomId, limit=20): ResponseEntity  // Python이 소환 시 호출
    → messageApi.getRecentMessages(roomId, limit.coerceIn(1, 50))
```

`limit.coerceIn(1, 50)`: 최소 1, 최대 50으로 클램핑. Python에서 기본 20을 요청한다.

---

### 5. `realtime/model/event.go`

Phase 2에서 추가된 WS 이벤트 타입 3개:

```go
type AgentJoinedEvent struct {
    Type "agent_joined"; AgentID, Role, Nickname string; X, Y float64
}
type AgentLeftEvent struct {
    Type "agent_left"; AgentID string
}
type AgentMessageEvent struct {
    Type "agent_message"; AgentID, Content string; Done bool
}
```

프론트는 `type` 필드로 분기한다. `AgentMessageEvent`의 `Done: false`가 스트리밍 청크, `Done: true`가 완료 신호.

---

### 6. `realtime/hub/hub.go`

Phase 2에서 추가된 에이전트 상태 관리.

**`AgentState` 구조체**
```go
type AgentState struct {
    AgentID  string
    CancelFn context.CancelFunc  // 스트리밍 취소용 (현재는 dismiss 시 호출)
}
```

**`roomState`에 추가된 필드**
```go
type roomState struct {
    clients map[*Client]bool
    ps      *redis.PubSub
    agent   *AgentState  // ← Phase 2 추가
}
```
방별로 에이전트 상태 하나를 가진다.

**새 메서드들**
```go
SetAgent(roomID, state)   // 에이전트 세션 등록
GetAgent(roomID)          // 에이전트 조회
ClearAgent(roomID)        // 에이전트 제거
HasAgent(roomID) bool     // 에이전트 존재 확인
```

**`Leave`에서 고아 에이전트 처리**
```go
func (h *Hub) Leave(c *Client) *AgentState {
    // ...
    if len(r.clients) == 0 {
        orphaned := r.agent  // 마지막 사람이 나갔을 때 에이전트가 있으면 반환
        r.ps.Close()
        delete(h.rooms, c.RoomID)
        return orphaned      // ws.go에서 이걸 받아서 cleanup 처리
    }
    return nil
}
```
"마지막 사람이 나가면 에이전트를 자동 정리"하는 로직. `Leave`가 고아 에이전트를 반환하면 `ws.go`의 `leave()`가 `cleanupAgent()`를 goroutine으로 호출한다.

**Prometheus 메트릭**
```go
var wsActiveConnections = promauto.NewGauge(prometheus.GaugeOpts{
    Name: "ws_active_connections_total",
})
```
`Join`에서 `.Inc()`, `Leave`에서 `.Dec()`. `/metrics` 엔드포인트로 Prometheus가 수집해간다.

---

### 7. `realtime/config/config.go`

Phase 2에서 추가된 설정 필드:
```go
AgentAPIURL: getEnv("AGENT_API_URL", "http://localhost:8082")
```
로컬에서는 `localhost:8082`, Docker에서는 `http://agolive-agent:8082`.

---

### 8. `realtime/handler/ws.go` ← Phase 2에서 가장 많이 바뀐 파일

**`Handler` 구조체에 추가된 것**
```go
agentC *http.Client  // SSE 전용 클라이언트, timeout 없음
```
왜 별도 클라이언트? `hc`는 5초 타임아웃이 있다. SSE는 Claude가 응답을 다 줄 때까지 연결이 유지되어야 하므로 타임아웃 없는 클라이언트를 따로 만들었다.

**`handleChat` 변경점**
```go
// 기존 채팅 저장/브로드캐스트 후 추가된 코드
if agent := h.hub.GetAgent(c.RoomID); agent != nil {
    go h.streamAgentResponse(agent.AgentID, c.RoomID, c.UserID, c.Nickname, msg.Content)
}
```
에이전트가 활성화된 방에서 채팅이 오면 goroutine으로 에이전트 응답 스트리밍을 시작한다. `go`로 띄우는 이유: 스트리밍이 완료될 때까지 기다리지 않고 바로 다음 메시지를 처리해야 하기 때문.

**`handleSummonAgent`**
```go
// 1. 이미 에이전트 있으면 에러
// 2. Python POST /internal/agent/sessions 호출
// 3. 응답에서 agentId, x, y, nickname, role 파싱
// 4. context.WithCancel로 CancelFn 생성 → Hub에 SetAgent
// 5. agent_joined 브로드캐스트
```
`context.WithCancel`로 만든 `CancelFn`은 나중에 `dismiss_agent`나 고아 정리 시 호출한다.
현재는 이 컨텍스트를 실제 스트리밍 중단에 연결하진 않았다 (`_ = agentCtx` 주석 참고). dismiss 시 Python의 `cancelled` 플래그로 중단하는 방식을 씀.

**`handleDismissAgent`**
```go
agent.CancelFn()
// Python DELETE 호출
h.hub.ClearAgent(c.RoomID)
h.hub.Publish(..., AgentLeftEvent{...})
```

**`streamAgentResponse`** ← SSE 파싱 로직
```go
func (h *Handler) streamAgentResponse(agentID, roomID string, userID int64, ...) {
    resp, _ := h.agentC.Do(req)  // timeout 없는 클라이언트
    
    buf := make([]byte, 4096)
    leftover := ""  // 버퍼 경계에서 잘린 SSE 라인 처리용
    
    for {
        n, err := resp.Body.Read(buf)
        if n > 0 {
            chunk := leftover + string(buf[:n])
            leftover = ""
            lines := strings.Split(chunk, "\n")
            for i, line := range lines {
                if i == len(lines)-1 {
                    leftover = line  // 마지막은 아직 완성 안 됐을 수 있음
                    continue
                }
                // "data: {...}" 파싱
                // AgentMessageEvent 브로드캐스트
                if event.Done { return }
            }
        }
        if err != nil { return }
    }
}
```
`leftover` 패턴이 핵심: TCP 스트림에서 Read할 때 SSE의 `data: {...}\n\n`이 버퍼 경계에서 잘릴 수 있다. 마지막 라인은 완성이 안 됐을 수 있으니 다음 Read에서 이어붙인다.

**`leave` 변경점 (고아 에이전트 정리)**
```go
func (h *Handler) leave(c *hub.Client) {
    // ...
    if orphaned := h.hub.Leave(c); orphaned != nil {
        orphaned.CancelFn()
        go h.cleanupAgent(orphaned.AgentID)  // Python DELETE 비동기 호출
    }
    // ...
}
```

---

### 9. `infra/docker/docker-compose.prod.yml`

**추가된 서비스들:**

```yaml
agent:
  image: .../agolive-agent:...
  environment:
    SPRING_API_URL: http://agolive-nginx:8090  # Nginx 내부 포트 경유
  # B/G 없음 — 에이전트는 상태를 메모리에만 가지므로 배포 시 세션 초기화 허용
```

```yaml
prometheus:
  volumes:
    - /opt/agolive/infra/monitoring/prometheus/prod.yml  # scrape 설정 마운트

node-exporter:
  pid: host  # EC2 프로세스 메트릭 수집을 위해 호스트 PID 네임스페이스 공유

grafana:
  environment:
    GF_SERVER_ROOT_URL: https://${DOMAIN}/grafana
    GF_SERVER_SERVE_FROM_SUB_PATH: "true"  # /grafana 서브패스로 서빙
```

---

### 10. `infra/monitoring/prometheus/prod.yml`

```yaml
scrape_configs:
  - job_name: 'agolive-api'
    metrics_path: '/actuator/prometheus'    # Spring Actuator
    targets: ['agolive-api-blue:8080', 'agolive-api-green:8080']  # B/G 양쪽 다 수집

  - job_name: 'agolive-realtime'
    metrics_path: '/metrics'                # Go promauto 자동 등록 엔드포인트
    targets: ['agolive-realtime-blue:8081', 'agolive-realtime-green:8081']

  - job_name: 'node-exporter'
    targets: ['agolive-node-exporter:9100'] # EC2 CPU/메모리/디스크 등
```

Blue/Green 양쪽을 동시에 scrape한다. 내려간 쪽은 자동으로 실패 처리되므로 문제없다.

---

### 11. `.github/workflows/deploy-agent.yml`

```yaml
on:
  push:
    paths: ['agolive-agent/**']  # agent 파일 변경 시만 실행
```

배포 흐름은 다른 서비스와 동일: ECR 빌드/푸시 → SSM Run Command → EC2에서 deploy.sh 실행.
단, agent는 B/G가 없으므로 deploy.sh에서 단순 컨테이너 재시작만 한다.

---

## 환경변수 추가분 (`.env.example`)

| 변수 | 용도 |
|---|---|
| `ANTHROPIC_API_KEY` | Claude API 호출 (Python agolive-agent) |
| `AGENT_API_URL` | Go → Python 내부 URL (기본: `http://agolive-agent:8082`) |
| `GRAFANA_ADMIN_USER` | Grafana 관리자 계정 |
| `GRAFANA_ADMIN_PASSWORD` | Grafana 관리자 비밀번호 |
| `AGENT_IMAGE_TAG` | ECR 이미지 태그 (운영 전용) |

`INTERNAL_SECRET`은 Phase 1부터 있던 변수인데, Phase 2에서 Go↔Python 간 인증에도 같이 쓰인다.

---

## 알아두면 좋은 설계 결정들

**왜 Python?**
Spring/Go로도 가능하지만 Anthropic Python SDK가 가장 성숙하고 FastAPI의 `StreamingResponse`가 SSE와 자연스럽게 맞는다.

**왜 인메모리 세션?**
에이전트는 방에 한 명이고 서버 재시작 시 세션이 날아가도 괜찮다 (재소환하면 됨). Redis나 DB에 저장하는 건 오버엔지니어링.

**왜 에이전트는 B/G 없음?**
에이전트 배포 시 진행 중인 세션이 끊기는 게 허용 가능하다. 채팅 서비스처럼 무중단이 필수인 게 아님.

**왜 `leftover` 패턴?**
TCP 스트림은 경계를 보장하지 않는다. 4096 바이트 버퍼로 읽다 보면 SSE의 `\n\n` 경계 중간에서 잘릴 수 있다. `leftover`는 이전 Read에서 완성 못 한 라인을 다음 Read에 이어붙이기 위한 버퍼다.
