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

router = APIRouter(prefix="/internal")

_sessions: dict[str, "AgentSession"] = {}
_lock = asyncio.Lock()

ROLE_CONFIGS: dict[str, dict] = {
    "helper": {
        "nickname": "AI 도우미",
        "system": (
            "당신은 협업 공간의 AI 도우미입니다. "
            "팀의 대화를 이해하고 질문에 답하거나 아이디어를 제안합니다. "
            "간결하고 친근하게 한국어로 답하세요. 100자 이내로 답하세요."
        ),
    },
    "summarizer": {
        "nickname": "AI 요약자",
        "system": (
            "당신은 협업 공간의 AI 요약자입니다. "
            "팀의 대화를 분석하고 핵심 내용을 요약하거나 정리합니다. "
            "간결하고 명확하게 한국어로 답하세요. 150자 이내로 답하세요."
        ),
    },
}

AGENT_X = 900.0
AGENT_Y = 200.0
MAX_HISTORY = 40  # 컨텍스트 윈도우 초과 방지


@dataclass
class AgentSession:
    agent_id: str
    room_id: str
    role: str
    x: float
    y: float
    nickname: str
    system_prompt: str
    history: list[dict] = field(default_factory=list)
    cancelled: bool = False


def verify_internal(x_internal_secret: str = Header(...)):
    if x_internal_secret != settings.internal_secret:
        raise HTTPException(status_code=401)


@router.post("/agent/sessions")
async def summon_agent(body: dict, _=Depends(verify_internal)):
    room_id = str(body["roomId"])
    role = body.get("role", "helper")

    async with _lock:
        if any(s.room_id == room_id for s in _sessions.values()):
            raise HTTPException(status_code=409, detail="AGENT_ALREADY_EXISTS")

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
        x=AGENT_X,
        y=AGENT_Y,
        nickname=config["nickname"],
        system_prompt=system_prompt,
    )

    async with _lock:
        _sessions[agent_id] = session

    return {
        "agentId": agent_id,
        "x": AGENT_X,
        "y": AGENT_Y,
        "nickname": config["nickname"],
        "role": role,
    }


@router.post("/agent/sessions/{agent_id}/message")
async def send_message(agent_id: str, body: dict, _=Depends(verify_internal)):
    session = _sessions.get(agent_id)
    if not session:
        raise HTTPException(status_code=404)

    user_content = f"유저 {body['userId']}: {body['content']}"
    session.history.append({"role": "user", "content": user_content})
    # 오래된 히스토리 제거 (항상 짝수 쌍 유지)
    if len(session.history) > MAX_HISTORY:
        session.history = session.history[-MAX_HISTORY:]

    return StreamingResponse(
        _stream_response(session),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@router.delete("/agent/sessions/{agent_id}")
async def dismiss_agent(agent_id: str, _=Depends(verify_internal)):
    session = _sessions.pop(agent_id, None)
    if session:
        session.cancelled = True
    return {"ok": True}


async def _stream_response(session: AgentSession) -> AsyncIterator[str]:
    client = anthropic.AsyncAnthropic(api_key=settings.anthropic_api_key)
    full_response = ""
    try:
        async with client.messages.stream(
            model="claude-haiku-4-5-20251001",
            max_tokens=512,
            system=session.system_prompt,
            messages=session.history,
        ) as stream:
            async for text in stream.text_stream:
                if session.cancelled:
                    break
                full_response += text
                yield f"data: {json.dumps({'content': text, 'done': False}, ensure_ascii=False)}\n\n"

        session.history.append({"role": "assistant", "content": full_response})
        yield f"data: {json.dumps({'content': '', 'done': True})}\n\n"
    except Exception as e:
        yield f"data: {json.dumps({'error': str(e), 'done': True})}\n\n"


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
