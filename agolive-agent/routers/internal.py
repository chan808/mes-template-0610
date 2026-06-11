import asyncio
import json
import uuid
from dataclasses import dataclass, field
from typing import AsyncIterator

import anthropic
import httpx
from fastapi import APIRouter, Depends, Header, HTTPException
from fastapi.responses import StreamingResponse

from config import settings

# S3 클라이언트는 지연 임포트 (aioboto3 미설치 환경 대비)
try:
    import aioboto3 as _aioboto3
    _AIOBOTO3_AVAILABLE = True
except ImportError:
    _AIOBOTO3_AVAILABLE = False

router = APIRouter(prefix="/internal")

_sessions: dict[str, "AgentSession"] = {}
_lock = asyncio.Lock()

MAX_AGENTS_PER_ROOM = 4
MAX_HISTORY = 40
# Go의 HitL 대기(120s)보다 길어야 사용자 응답이 유실되지 않는다
TOOL_RESULT_TIMEOUT_SECONDS = 180.0

# request_human_input 툴: 모든 역할에서 사용 가능 (Phase 3-B)
REQUEST_HUMAN_INPUT_TOOL = {
    "name": "request_human_input",
    "description": "사용자에게 확인이나 추가 정보를 요청합니다. 중요한 결정이나 불명확한 상황에서 사용하세요.",
    "input_schema": {
        "type": "object",
        "properties": {
            "prompt": {"type": "string", "description": "사용자에게 보여줄 질문"},
            "options": {
                "type": "array",
                "items": {"type": "string"},
                "description": "선택지 목록 (생략 가능, 생략 시 자유 입력)",
            },
        },
        "required": ["prompt"],
    },
}

# delegate_to_worker 툴: orchestrator 역할 전용 (Phase 3-C)
DELEGATE_TO_WORKER_TOOL = {
    "name": "delegate_to_worker",
    "description": "특정 역할의 워커 에이전트에게 작업을 위임합니다. 복잡한 태스크를 분해할 때 사용하세요.",
    "input_schema": {
        "type": "object",
        "properties": {
            "role": {
                "type": "string",
                "enum": ["summarizer", "researcher", "critic"],
                "description": "워커의 역할",
            },
            "task": {"type": "string", "description": "워커에게 위임할 구체적인 작업"},
        },
        "required": ["role", "task"],
    },
}

# create_document 툴: 파일 결과물 생성 (Phase 3-D)
CREATE_DOCUMENT_TOOL = {
    "name": "create_document",
    "description": "텍스트 문서를 생성하고 다운로드 링크를 제공합니다.",
    "input_schema": {
        "type": "object",
        "properties": {
            "filename": {"type": "string", "description": "파일명 (확장자 포함, 예: summary.md)"},
            "content": {"type": "string", "description": "파일 내용"},
            "mime_type": {
                "type": "string",
                "enum": ["text/plain", "text/markdown", "text/csv"],
                "description": "파일 형식",
            },
        },
        "required": ["filename", "content"],
    },
}

ROLE_CONFIGS: dict[str, dict] = {
    "helper": {
        "nickname": "AI 도우미",
        "system": (
            "당신은 협업 공간의 AI 도우미입니다. "
            "팀의 대화를 이해하고 질문에 답하거나 아이디어를 제안합니다. "
            "간결하고 친근하게 한국어로 답하세요. 100자 이내로 답하세요."
        ),
        "tools": [REQUEST_HUMAN_INPUT_TOOL],
    },
    "summarizer": {
        "nickname": "AI 요약자",
        "system": (
            "당신은 협업 공간의 AI 요약자입니다. "
            "팀의 대화를 분석하고 핵심 내용을 요약하거나 정리합니다. "
            "간결하고 명확하게 한국어로 답하세요. 150자 이내로 답하세요."
        ),
        "tools": [REQUEST_HUMAN_INPUT_TOOL],
    },
    "researcher": {
        "nickname": "AI 조사원",
        "system": (
            "당신은 협업 공간의 AI 조사원입니다. "
            "주어진 주제를 분석하고 관련 정보, 배경 지식, 고려사항을 제공합니다. "
            "논리적이고 체계적으로 한국어로 답하세요. 200자 이내로 답하세요."
        ),
        "tools": [REQUEST_HUMAN_INPUT_TOOL, CREATE_DOCUMENT_TOOL],
    },
    "critic": {
        "nickname": "AI 검토자",
        "system": (
            "당신은 협업 공간의 AI 검토자입니다. "
            "아이디어나 계획의 잠재적 문제점, 개선 방향을 지적합니다. "
            "건설적이고 구체적으로 한국어로 답하세요. 150자 이내로 답하세요."
        ),
        "tools": [REQUEST_HUMAN_INPUT_TOOL],
    },
    "orchestrator": {
        "nickname": "AI 코디네이터",
        "system": (
            "당신은 협업 공간의 AI 코디네이터입니다. "
            "복잡한 요청을 받으면 적절한 전문가(요약자, 조사원, 검토자)에게 작업을 위임하고 "
            "결과를 통합해 최종 답변을 제공합니다. "
            "필요하면 사용자에게 추가 정보를 요청하세요. "
            "최종 답변은 간결하게 한국어로 작성하세요."
        ),
        "tools": [REQUEST_HUMAN_INPUT_TOOL, DELEGATE_TO_WORKER_TOOL],
    },
}


@dataclass
class AgentSession:
    agent_id: str
    room_id: str
    role: str
    x: float
    y: float
    nickname: str
    system_prompt: str
    tools: list[dict]
    history: list[dict] = field(default_factory=list)
    cancelled: bool = False
    # tool_use 완료 대기 (Phase 3-B/C)
    _tool_event: asyncio.Event = field(default_factory=asyncio.Event)
    _tool_results: list[dict] = field(default_factory=list)
    # 세션당 스트림 직렬화 — 연속 채팅 시 history/_tool_event 동시 변형 방지
    _stream_lock: asyncio.Lock = field(default_factory=asyncio.Lock)


def verify_internal(x_internal_secret: str = Header(...)):
    if x_internal_secret != settings.internal_secret:
        raise HTTPException(status_code=401)


def truncate_history(history: list[dict], max_len: int = MAX_HISTORY) -> list[dict]:
    """히스토리를 최대 길이로 절단하되 tool_use/tool_result 쌍이 깨지지 않게 유지한다.

    단순 슬라이스는 tool_result(user 메시지의 list content)가 선행 tool_use 없이
    맨 앞에 남아 Claude API 400을 유발할 수 있다. 절단 후 첫 메시지가
    일반 텍스트 user 메시지가 될 때까지 앞에서 추가로 제거한다.
    """
    if len(history) <= max_len:
        return history
    trimmed = history[-max_len:]
    while trimmed and not (
        trimmed[0]["role"] == "user" and isinstance(trimmed[0]["content"], str)
    ):
        trimmed.pop(0)
    return trimmed


@router.post("/agent/sessions")
async def summon_agent(body: dict, _=Depends(verify_internal)):
    room_id = str(body["roomId"])
    role = body.get("role", "helper")
    # 타일 좌표 (realtime이 항상 전달 — 미전달 시 기본 슬롯 0 위치)
    x = float(body.get("x", 22.0))
    y = float(body.get("y", 5.0))

    config = ROLE_CONFIGS.get(role, ROLE_CONFIGS["helper"])
    agent_id = str(uuid.uuid4())

    # 소환 시점의 최근 대화 컨텍스트 로드
    recent = await _fetch_recent_messages(room_id)
    system_prompt = config["system"]
    if recent:
        context_lines = "\n".join(
            f"유저 {m['userId']}: {m['content']}"
            for m in recent
            if m.get("type") == "chat"
        )
        if context_lines:
            system_prompt += f"\n\n[입장 전 대화]\n{context_lines}"

    session = AgentSession(
        agent_id=agent_id,
        room_id=room_id,
        role=role,
        x=x,
        y=y,
        nickname=config["nickname"],
        system_prompt=system_prompt,
        tools=config.get("tools", []),
    )

    # 정원 확인과 등록을 한 락 안에서 처리 (동시 소환 race 방지)
    async with _lock:
        room_sessions = [s for s in _sessions.values() if s.room_id == room_id]
        if len(room_sessions) >= MAX_AGENTS_PER_ROOM:
            raise HTTPException(status_code=409, detail="AGENT_LIMIT_EXCEEDED")
        # 동일 역할 중복 소환 시 사용 중이지 않은 가장 작은 번호 부여 (퇴장 후 재소환 시 중복 방지)
        used_nicknames = {s.nickname for s in room_sessions if s.role == role}
        if config["nickname"] in used_nicknames:
            n = 2
            while f"{config['nickname']} {n}" in used_nicknames:
                n += 1
            session.nickname = f"{config['nickname']} {n}"
        _sessions[agent_id] = session

    return {
        "agentId": agent_id,
        "x": x,
        "y": y,
        "nickname": session.nickname,
        "role": role,
    }


@router.post("/agent/sessions/{agent_id}/message")
async def send_message(agent_id: str, body: dict, _=Depends(verify_internal)):
    session = _sessions.get(agent_id)
    if not session:
        raise HTTPException(status_code=404)

    user_content = f"유저 {body['userId']}: {body['content']}"

    return StreamingResponse(
        _stream_response(session, user_content),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@router.post("/agent/sessions/{agent_id}/tool_result")
async def inject_tool_result(agent_id: str, body: dict, _=Depends(verify_internal)):
    """Go가 도구 실행 결과를 주입한다 (HitL 응답 포함)."""
    session = _sessions.get(agent_id)
    if not session:
        raise HTTPException(status_code=404)
    if session._tool_event.is_set():
        raise HTTPException(status_code=409, detail="TOOL_ALREADY_RESOLVED")

    session._tool_results = body.get("results", [])
    session._tool_event.set()
    return {"ok": True}


@router.delete("/agent/sessions/{agent_id}")
async def dismiss_agent(agent_id: str, _=Depends(verify_internal)):
    session = _sessions.pop(agent_id, None)
    if session:
        session.cancelled = True
        # 대기 중인 tool_event가 있으면 해제해 goroutine leak 방지
        session._tool_event.set()
    return {"ok": True}


def _build_stream_kwargs(session: AgentSession) -> dict:
    """Claude API 스트리밍 호출 인자를 구성한다."""
    kwargs: dict = {
        "model": "claude-haiku-4-5-20251001",
        "max_tokens": 1024,
        "system": session.system_prompt,
        "messages": session.history,
    }
    if session.tools:
        kwargs["tools"] = session.tools
        # Go가 tool_result를 블록 단위로 따로 주입하므로 턴당 tool_use 1개만 허용
        # (병렬 블록 허용 시 결과 1개만 반영되어 다음 호출이 400으로 실패)
        kwargs["tool_choice"] = {"type": "auto", "disable_parallel_tool_use": True}
    return kwargs


async def _stream_response(session: AgentSession, user_content: str) -> AsyncIterator[str]:
    """세션 단위 락으로 직렬화된 SSE 스트리밍 응답 생성기.

    같은 세션의 동시 스트림은 history와 _tool_event를 공유 변형하므로
    락으로 직렬화한다. 연속 채팅은 순차적으로 응답된다.
    """
    async with session._stream_lock:
        if session.cancelled:
            return

        # 직렬화 확정 후 기록해야 user → assistant 턴 순서가 보존된다
        session.history.append({"role": "user", "content": user_content})
        # 오래된 히스토리 제거 (tool_use/tool_result 쌍 보존)
        session.history = truncate_history(session.history)

        async for chunk in _run_agent_loop(session):
            yield chunk


async def _run_agent_loop(session: AgentSession) -> AsyncIterator[str]:
    """tool_use 루프를 지원하는 응답 생성 코어 (스트림 락 보유 상태에서 호출)."""
    client = anthropic.AsyncAnthropic(api_key=settings.anthropic_api_key)

    while True:
        if session.cancelled:
            return

        session._tool_event.clear()
        session._tool_results = []

        full_text = ""
        tool_use_blocks: list = []

        try:
            stream_kwargs = _build_stream_kwargs(session)

            async with client.messages.stream(**stream_kwargs) as stream:
                async for event in stream:
                    if session.cancelled:
                        return

                    # 텍스트 델타 실시간 스트리밍
                    if (
                        event.type == "content_block_delta"
                        and hasattr(event.delta, "text")
                    ):
                        full_text += event.delta.text
                        yield f"data: {json.dumps({'content': event.delta.text, 'done': False}, ensure_ascii=False)}\n\n"

                final_msg = await stream.get_final_message()

            # tool_use 블록 수집
            for block in final_msg.content:
                if block.type == "tool_use":
                    tool_use_blocks.append(block)

        except Exception as e:
            yield f"data: {json.dumps({'content': '', 'done': True, 'error': str(e)})}\n\n"
            return

        if final_msg.stop_reason == "tool_use" and tool_use_blocks:
            # assistant 응답을 히스토리에 추가 (tool_use 블록 포함)
            session.history.append(
                {"role": "assistant", "content": [b.model_dump() for b in final_msg.content]}
            )

            # Go에 tool_use 이벤트 전달
            for block in tool_use_blocks:
                yield (
                    f"data: {json.dumps({'type': 'tool_use', 'toolName': block.name, 'toolInput': block.input, 'toolUseId': block.id, 'done': False}, ensure_ascii=False)}\n\n"
                )

            # Go가 tool_result를 주입할 때까지 대기 (keepalive 포함)
            deadline = asyncio.get_event_loop().time() + TOOL_RESULT_TIMEOUT_SECONDS
            while not session._tool_event.is_set():
                if session.cancelled:
                    return
                remaining = deadline - asyncio.get_event_loop().time()
                if remaining <= 0:
                    yield f"data: {json.dumps({'content': '', 'done': True})}\n\n"
                    return
                try:
                    await asyncio.wait_for(
                        asyncio.shield(session._tool_event.wait()),
                        timeout=min(5.0, remaining),
                    )
                except asyncio.TimeoutError:
                    # SSE keepalive: nginx 등 프록시 연결 유지
                    yield ": keepalive\n\n"

            if session.cancelled:
                return

            # 방어: 응답이 누락된 tool_use 블록을 채워 히스토리 무결성 보장 (API 400 방지)
            received = {r.get("tool_use_id") for r in session._tool_results}
            for block in tool_use_blocks:
                if block.id not in received:
                    session._tool_results.append(
                        {"type": "tool_result", "tool_use_id": block.id, "content": "(결과 누락)"}
                    )

            # tool_result를 히스토리에 추가하고 루프 재진행
            session.history.append({"role": "user", "content": session._tool_results})
            continue

        # stop_reason == "end_turn": 정상 완료
        if full_text:
            session.history.append({"role": "assistant", "content": full_text})
        yield f"data: {json.dumps({'content': '', 'done': True})}\n\n"
        return


@router.post("/files")
async def upload_file(body: dict, _=Depends(verify_internal)):
    """create_document 툴 결과물을 S3에 업로드하고 presigned URL을 반환한다."""
    if not _AIOBOTO3_AVAILABLE or not settings.aws_s3_bucket:
        raise HTTPException(status_code=503, detail="S3 미설정")

    filename = body.get("filename", "document.txt")
    content = body.get("content", "")
    mime_type = body.get("mime_type", "text/plain")

    key = f"agent-files/{uuid.uuid4()}/{filename}"

    session = _aioboto3.Session(
        aws_access_key_id=settings.aws_access_key_id,
        aws_secret_access_key=settings.aws_secret_access_key,
        region_name=settings.aws_region,
    )
    async with session.client("s3") as s3:
        await s3.put_object(
            Bucket=settings.aws_s3_bucket,
            Key=key,
            Body=content.encode("utf-8"),
            ContentType=mime_type,
        )
        # presigned URL: 7일 유효 (채팅 히스토리 보존 기간과 동일)
        url = await s3.generate_presigned_url(
            "get_object",
            Params={"Bucket": settings.aws_s3_bucket, "Key": key},
            ExpiresIn=7 * 24 * 3600,
        )

    return {"url": url, "filename": filename, "mimeType": mime_type}


async def _fetch_recent_messages(room_id: str) -> list[dict]:
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            resp = await client.get(
                f"{settings.spring_api_url}/internal/rooms/{room_id}/messages/context",
                headers={"X-Internal-Secret": settings.internal_secret},
            )
            if resp.is_success:
                return resp.json().get("data", [])
    except Exception:
        pass
    return []
