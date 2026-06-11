# Phase 3 구현 설명

Phase 2까지 완성된 "방 당 에이전트 1개, 텍스트 스트리밍" 구조를 4가지 기능으로 확장했다.

- **3-A** 멀티 에이전트 (방 당 최대 4개)
- **3-B** Tool Use + Human-in-the-loop
- **3-C** Orchestrator (에이전트가 에이전트를 지휘)
- **3-D** S3 파일 결과물

---

## 전체 흐름 한눈에

```
사용자
  │  summon_agent {role}
  ▼
realtime (Go)
  │  POST /internal/agent/sessions {roomId, role, x, y}
  ▼
agolive-agent (Python)
  │  AgentSession 생성, SSE 엔드포인트 열어둠
  ▼
realtime (Go)
  │  Hub에 AgentState 등록, agent_joined 브로드캐스트
  ▼
사용자 채팅 (@mention or 전체)
  ▼
realtime (Go) → POST /internal/agent/sessions/{id}/message
  ▼
agolive-agent (Python)
  │  Claude Haiku SSE 스트리밍
  │    ├─ 텍스트 청크: {content, done: false}
  │    ├─ 도구 요청:   {type:"tool_use", toolName, toolInput, toolUseId}
  │    └─ 완료:        {done: true}
  ▼
realtime (Go)
  │  텍스트: agent_message 브로드캐스트
  │  tool_use: 도구 실행 → POST /tool_result → Python 재개
  ▼
Frontend
  agent_message → 채팅창 스트리밍
  agent_needs_input → HumanInLoopDialog 표시
  agent_thinking → 시스템 메시지
  agent_file → 파일 카드
```

---

## 변경된 파일 설명

---

### `realtime/hub/hub.go` — Hub 멀티 에이전트 지원

**Phase 2의 문제**: `roomState`에 `agent *AgentState` 단수 포인터가 있어서 방당 에이전트 1개만 가능했다.

**변경 내용**:

```go
// 변경 전
type roomState struct {
    agent *AgentState  // 단수
}

// 변경 후
type roomState struct {
    agents map[string]*AgentState  // agentId → state
}
```

`AgentState`에 추가된 필드:
- `Role`, `Nickname`, `X`, `Y`: 신규 입장자에게 에이전트 목록 전송할 때 필요
- `HumanInputCh chan string`: HitL에서 사용자 응답을 `streamAgentResponse` 고루틴에 전달하는 채널

Hub API 변화:
- 기존: `SetAgent`, `GetAgent`, `ClearAgent`, `HasAgent`, `Leave → *AgentState`
- 신규: `AddAgent`, `RemoveAgent`, `GetAgents`, `GetAgent(roomID, agentID)`, `AgentCount`, `Leave → []*AgentState`

`Leave`가 슬라이스를 반환하는 이유: 마지막 유저 퇴장 시 방에 있던 에이전트가 여러 개일 수 있으므로 전부 정리해야 한다.

---

### `realtime/model/event.go` — 이벤트 타입 추가

`ClientMessage`에 추가된 필드:
- `AgentID string`: `dismiss_agent` 시 어떤 에이전트를 퇴장시킬지 지정
- `Response string`: `agent_input` (HitL 사용자 응답) 전달

신규 서버→클라이언트 이벤트:
- `AgentNeedsInputEvent`: 에이전트가 사용자에게 확인을 요청할 때 (`{agentId, toolUseId, prompt, options}`)
- `AgentThinkingEvent`: orchestrator가 작업을 위임 중임을 표시 (`{agentId, step}`)
- `AgentFileEvent`: 에이전트가 파일을 생성했을 때 다운로드 링크 전달 (`{agentId, filename, url, mimeType}`)

---

### `realtime/handler/ws.go` — WS 핸들러 전면 업데이트

**handleSummonAgent** 변경:
- 기존: `HasAgent` 체크 → 이미 있으면 에러
- 변경: `AgentCount >= 4`이면 에러, 없으면 허용
- 추가: 현재 에이전트 수를 보고 위치 배열(`agentPositions`)에서 겹치지 않는 좌표 선택
- 추가: Go가 x,y를 결정하고 Python에 전달 (Python은 받은 값 그대로 사용)

**handleDismissAgent** 변경:
- 기존: 방의 단일 에이전트 퇴장
- 변경: `msg.AgentID`로 특정 에이전트 퇴장

**handleChat** 변경:
- 기존: 에이전트가 있으면 단일 에이전트에게 전달
- 변경: `@닉네임` 포함 → 해당 에이전트에게만 / 없으면 모든 에이전트에게

**enter** 변경:
- 추가: 신규 입장자에게 현재 활성 에이전트 목록 전송 (`agents_snapshot`)

**leave** 변경:
- `hub.Leave` 반환값이 `[]*AgentState`이므로 전부 순회해서 정리

**handleAgentInput** 신규:
- WS 이벤트 `agent_input {agentId, response}` 수신
- `hub.GetAgent`로 해당 에이전트의 `HumanInputCh`에 응답을 push
- 비블로킹 (`select { case ch <- response: default: }`) — 이미 응답이 있거나 대기 중이 아니면 무시

---

#### `consumeAgentSSE` + `handleToolUse` — tool_use 처리 핵심

`streamAgentResponse`는 이전과 동일하게 SSE HTTP 요청을 열고 응답을 읽는다.  
달라진 것은 SSE 파싱 로직이 `consumeAgentSSE`로 분리되고 이벤트 `type` 필드를 처리한다.

```go
// SSE 이벤트 구조 (type 필드로 분기)
type sseEvent struct {
    Type      string          // "", "text" → 텍스트 / "tool_use" / "file"
    Content   string
    Done      bool
    ToolName  string          // tool_use 시
    ToolInput json.RawMessage // tool_use 시 (JSON 원본 보존)
    ToolUseID string
    // ... needs_input, file 필드들
}
```

`handleToolUse`는 `toolName` 기준으로 분기:

| toolName | 처리 |
|---|---|
| `request_human_input` | `agent_needs_input` 브로드캐스트 → `HumanInputCh` 대기(120초) → `sendToolResult` |
| `delegate_to_worker` | `executeDelegateToWorker` → `sendToolResult` |
| `create_document` | `executeCreateDocument` → `sendToolResult` |
| 기타 | "지원하지 않는 도구" 반환 |

**sendToolResult**:
```
POST /internal/agent/sessions/{id}/tool_result
body: {results: [{type:"tool_result", tool_use_id, content}]}
```
→ Python의 `_tool_event`를 set해서 대기 중이던 streaming loop를 재개시킨다.

---

#### `executeDelegateToWorker` — 오케스트레이터 워커 관리

orchestrator가 `delegate_to_worker {role, task}` 도구를 호출하면:

1. `agent_thinking` 브로드캐스트 → 프론트엔드에 "요약자에게 작업 위임 중..." 표시
2. Python API로 워커 에이전트 소환 (위치는 현재 에이전트 수 기준)
3. Hub에 워커 등록 + `agent_joined` 브로드캐스트
4. `streamWorkerAndCollect`로 워커 응답 스트리밍 (사용자에게도 보임) + 텍스트 누적
5. 워커 정리: DELETE 세션 + Hub 제거 + `agent_left` 브로드캐스트
6. 누적된 워커 응답 텍스트를 orchestrator의 `tool_result`로 주입

**streamWorkerAndCollect**는 `streamAgentResponse`와 비슷하지만 응답 텍스트를 `strings.Builder`로 누적해서 반환한다.  
워커가 또다시 tool_use를 하면 `"도구 중첩 미지원"` 응답으로 차단 (무한 재귀 방지).

---

### `agolive-agent/routers/internal.py` — 핵심 변경

#### AgentSession 새 필드

```python
@dataclass
class AgentSession:
    ...
    tools: list[dict]          # 역할별 사용 가능 도구
    _tool_event: asyncio.Event # Go가 tool_result 주입 시 set
    _tool_results: list[dict]  # 주입된 tool_result 내용
```

#### _stream_response — tool_use 루프로 전환

**기존 구조**: `client.messages.stream()` 한 번 호출 → 텍스트 yield → 끝

**변경 구조**: `while True:` 루프
```
1. _tool_event 초기화
2. Claude API 스트리밍 → 텍스트 청크 실시간 yield
3. 스트리밍 완료 후 final_message 확인
4. stop_reason == "tool_use":
   - tool_use 이벤트 yield (Go에 알림)
   - _tool_event 대기 (최대 60초, 5초마다 SSE keepalive)
   - Go가 주입한 _tool_results를 history에 추가
   - 루프 처음으로
5. stop_reason == "end_turn":
   - {done: true} yield
   - return
```

keepalive가 필요한 이유: Go가 tool_result를 주입할 때까지 SSE 연결이 유지되어야 하는데, nginx 같은 프록시가 idle 연결을 끊을 수 있다. 5초마다 `: keepalive\n\n` (SSE 주석)을 보내서 연결을 유지한다.

#### 새 엔드포인트

**POST /internal/agent/sessions/{id}/tool_result**:
- Go가 도구 실행 결과를 주입
- `_tool_event.set()` → 대기 중인 streaming loop 재개
- 이미 set 상태면 409 (중복 주입 방지)

**POST /internal/files**:
- `create_document` 도구 결과를 S3에 업로드
- aioboto3로 비동기 S3 업로드 → presigned URL (7일) 반환
- S3 미설정(`AWS_S3_BUCKET` 비어있음)이면 503

#### 도구 정의 구조

```
REQUEST_HUMAN_INPUT_TOOL   → 모든 역할에 포함
DELEGATE_TO_WORKER_TOOL    → orchestrator 전용
CREATE_DOCUMENT_TOOL       → researcher, orchestrator에 포함
```

도구가 `session.tools`에 있으면 Claude API 호출 시 `tools=` 파라미터로 전달된다.  
도구가 없으면 `anthropic.NOT_GIVEN` (파라미터 자체를 생략) → Claude는 tool_use 블록을 반환하지 않는다.

#### 역할 확장

| 역할 | 닉네임 | 도구 | 용도 |
|---|---|---|---|
| helper | AI 도우미 | HitL | 일반 질답 |
| summarizer | AI 요약자 | HitL | 대화 요약 |
| researcher | AI 조사원 | HitL, create_document | 조사 + 문서 생성 |
| critic | AI 검토자 | HitL | 아이디어 검토 |
| orchestrator | AI 코디네이터 | HitL, delegate_to_worker | 복잡한 태스크 분해 |

---

### `agolive-agent/config.py` — S3 설정 추가

```python
aws_access_key_id: str = ""
aws_secret_access_key: str = ""
aws_region: str = "ap-northeast-2"
aws_s3_bucket: str = ""
```

모두 선택 사항 (기본값 빈 문자열). 버킷이 비어있으면 `/internal/files` 엔드포인트가 503을 반환하므로 S3 없이도 나머지 기능은 정상 동작한다.

---

### `agolive-agent/pyproject.toml`

`aioboto3>=13.0` 추가. S3 비동기 업로드에 사용.

---

### Frontend 변경

#### `types/ws.ts`

**ClientMessage 변경**:
```typescript
// 기존
{ type: "dismiss_agent" }
// 변경 — 어떤 에이전트인지 지정
{ type: "dismiss_agent"; agentId: string }

// 신규
{ type: "agent_input"; agentId: string; response: string }
```

**AgentRole** 타입 추출: `"helper" | "summarizer" | "researcher" | "critic" | "orchestrator"`

**ServerMessage 신규 이벤트**:
```typescript
{ type: "agent_needs_input"; agentId: string; toolUseId: string; prompt: string; options: string[] }
{ type: "agent_thinking";    agentId: string; step: string }
{ type: "agent_file";        agentId: string; filename: string; url: string; mimeType: string }
```

**DisplayMessage 신규 타입**:
```typescript
{ type: "file"; agentId: string; filename: string; url: string; mimeType: string; ... }
```

**HumanInputRequest 인터페이스 추가**: HitL 다이얼로그 상태를 저장하는 타입.

---

#### `stores/wsStore.ts`

`humanInputRequest: HumanInputRequest | null` 상태 추가.  
`setHumanInputRequest` action 추가.  
`reset` 시 함께 초기화.

HitL 상태를 wsStore에 넣은 이유: WS 이벤트로 들어오고 WS 관련 액션(`agent_input` 전송)으로 닫히므로 WS 스토어가 적절.

---

#### `hooks/useWebSocket.ts`

신규 이벤트 핸들러 추가:

**`agent_needs_input`**:
```typescript
setHumanInputRequest({
  agentId, toolUseId, agentNickname: agent?.nickname,
  prompt, options
})
```
→ `HumanInLoopDialog`가 이 상태를 읽어서 모달 표시.

**`agent_thinking`**:
→ 시스템 메시지로 채팅창에 추가 (`"AI 코디네이터: 요약자에게 작업 위임 중..."`)

**`agent_file`**:
→ `type: "file"` DisplayMessage로 채팅창에 추가 (`ChatPanel`에서 파일 카드로 렌더링)

**`agent_left`** 변경:
→ 퇴장한 에이전트에 대한 HitL 다이얼로그가 열려있으면 자동으로 닫음 (`setHumanInputRequest(null)`)

---

#### `components/MemberList.tsx`

**에이전트 목록**: 각 에이전트 우측에 ✕ 버튼 → `dismiss_agent {agentId}` 전송

**소환 UI 변경**:
- 기존: hasAgent이면 "에이전트 퇴장" 버튼 하나
- 변경: 에이전트 수가 4 미만이면 5개 역할 버튼 표시 (orchestrator는 전체 폭)
- 4개 도달하면 "최대 4개 소환됨" 표시

---

#### `components/HumanInLoopDialog.tsx` — 신규

`wsStore.humanInputRequest`가 null이 아닐 때 전체 화면 오버레이로 표시.

구성:
- 에이전트 이름 + 질문 표시
- 선택지가 있으면 버튼으로 표시 (클릭 시 즉시 응답)
- 자유 입력 텍스트박스 (Enter로 전송)
- ESC: `"(사용자 응답하지 않았습니다)"` 로 에이전트 재개

응답 전송 방법: `onSend({ type: "agent_input", agentId, response })` → Go `handleAgentInput` → `HumanInputCh` → `sendToolResult` → Python 재개.

---

#### `components/ChatPanel.tsx`

`type: "file"` DisplayMessage 렌더링 추가:
```tsx
<a href={msg.url} target="_blank">
  📄 {msg.filename} ↓
</a>
```

---

#### `components/RoomSpaceView.tsx`

`<HumanInLoopDialog onSend={send} />`를 최상단 레이아웃 바깥(`<>` fragment)에 추가.  
채팅 패널이나 캔버스에 묻히지 않고 항상 전체 오버레이로 표시되도록.

---

## 주요 설계 결정

### Go가 모든 도구 실행을 담당

Python이 `tool_use` 이벤트를 yield하면, Go가:
1. 어떤 도구인지 파악
2. 실행 (HitL이면 채널 대기, 워커이면 새 에이전트 소환, 파일이면 Python API 호출)
3. 결과를 `/tool_result`로 Python에 주입

장점: Python streaming loop는 Claude API 호출 + SSE yield에만 집중. 도구 실행 로직 분리.  
단점: 새 도구 추가 시 Go도 수정해야 함.

### HumanInputCh는 버퍼 크기 1

```go
HumanInputCh: make(chan string, 1)
```

`handleAgentInput`은 비블로킹 send를 사용한다:
```go
select {
case agent.HumanInputCh <- msg.Response:
default:  // 이미 응답 있거나 대기 중 아님 → 무시
}
```

사용자가 동시에 두 번 클릭하거나 다이얼로그가 닫힌 후 전송을 시도해도 goroutine이 블로킹되지 않는다.

### tool_use 루프의 keepalive

Python이 `_tool_event.wait()`로 Go의 tool_result를 기다리는 동안 SSE 연결은 열려있어야 한다. nginx 등 프록시가 idle timeout으로 연결을 끊을 수 있어서 5초마다 SSE 주석(`: keepalive\n\n`)을 yield한다. Go의 SSE 파서는 `data: ` prefix가 없으면 무시하므로 문제없다.

### 워커 중첩 방지

`streamWorkerAndCollect`에서 워커가 tool_use를 반환하면:
```go
case "tool_use":
    h.sendToolResult(ctx, agentID, ev.ToolUseID, "(도구 중첩 미지원)")
```
워커는 HitL이나 delegate_to_worker를 사용할 수 없다. 무한 재귀를 방지.

---

## 환경변수 추가 (agolive-agent)

```bash
# S3 파일 업로드 (선택)
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
AWS_REGION=ap-northeast-2
AWS_S3_BUCKET=
```

미설정 시 `create_document` 도구가 503을 반환하지만, 나머지 기능(멀티 에이전트, HitL, orchestrator)은 정상 동작한다.
