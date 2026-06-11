import asyncio
import os
from types import SimpleNamespace

# settings 로드 전에 테스트용 환경변수 주입
os.environ.setdefault("ANTHROPIC_API_KEY", "test-key")
os.environ.setdefault("INTERNAL_SECRET", "test-secret")

import pytest
from fastapi.testclient import TestClient

from main import app
from routers import internal
from routers.internal import (
    MAX_HISTORY,
    ROLE_CONFIGS,
    AgentSession,
    _build_stream_kwargs,
    truncate_history,
)


def _text(role: str, i: int) -> dict:
    return {"role": role, "content": f"메시지 {i}"}


def _tool_use_pair(i: int) -> list[dict]:
    return [
        {"role": "assistant", "content": [{"type": "tool_use", "id": f"tu_{i}", "name": "t", "input": {}}]},
        {"role": "user", "content": [{"type": "tool_result", "tool_use_id": f"tu_{i}", "content": "ok"}]},
    ]


def _make_session(role: str = "helper", tools: list | None = None) -> AgentSession:
    config = ROLE_CONFIGS[role]
    return AgentSession(
        agent_id="test-agent",
        room_id="1",
        role=role,
        x=22.0,
        y=5.0,
        nickname=config["nickname"],
        system_prompt=config["system"],
        tools=config["tools"] if tools is None else tools,
    )


class TestBuildStreamKwargs:
    def test_도구있는세션_병렬tooluse_비활성화(self):
        # Go가 tool_result를 블록별로 따로 주입하므로 턴당 tool_use는 1개여야 한다
        session = _make_session(role="orchestrator")

        kwargs = _build_stream_kwargs(session)

        assert kwargs["tools"] == session.tools
        assert kwargs["tool_choice"] == {"type": "auto", "disable_parallel_tool_use": True}

    def test_도구없는세션_tools와_toolchoice_미설정(self):
        # tools 없이 tool_choice를 보내면 Claude API가 400을 반환한다
        session = _make_session(tools=[])

        kwargs = _build_stream_kwargs(session)

        assert "tools" not in kwargs
        assert "tool_choice" not in kwargs

    def test_히스토리와_시스템프롬프트_포함(self):
        session = _make_session()
        session.history.append({"role": "user", "content": "안녕"})

        kwargs = _build_stream_kwargs(session)

        assert kwargs["messages"] is session.history
        assert kwargs["system"] == session.system_prompt


class TestStreamSerialization:
    @pytest.mark.asyncio
    async def test_동시메시지_스트림_직렬실행(self, monkeypatch):
        # 같은 세션에 동시 스트림이 겹치면 history가 오염되므로 직렬 실행돼야 한다
        tracker = {"active": 0, "max_active": 0}

        class _FakeStream:
            async def __aenter__(self):
                tracker["active"] += 1
                tracker["max_active"] = max(tracker["max_active"], tracker["active"])
                await asyncio.sleep(0.02)  # 스트리밍 구간에서 제어 양보 → 동시성 노출
                return self

            async def __aexit__(self, *args):
                tracker["active"] -= 1
                return False

            def __aiter__(self):
                self._sent = False
                return self

            async def __anext__(self):
                if self._sent:
                    raise StopAsyncIteration
                self._sent = True
                return SimpleNamespace(
                    type="content_block_delta", delta=SimpleNamespace(text="응답")
                )

            async def get_final_message(self):
                return SimpleNamespace(content=[], stop_reason="end_turn")

        class _FakeAnthropic:
            def __init__(self, api_key):
                self.messages = SimpleNamespace(stream=lambda **kwargs: _FakeStream())

        monkeypatch.setattr(internal.anthropic, "AsyncAnthropic", _FakeAnthropic)
        session = _make_session(tools=[])

        async def consume(content: str):
            async for _ in internal._stream_response(session, content):
                pass

        await asyncio.gather(consume("유저 1: 안녕"), consume("유저 1: 빨리"))

        assert tracker["max_active"] == 1
        assert [m["role"] for m in session.history] == [
            "user", "assistant", "user", "assistant",
        ]


class TestTruncateHistory:
    def test_최대길이이하_그대로유지(self):
        history = [_text("user", 0), _text("assistant", 1)]

        assert truncate_history(history) == history

    def test_초과시_최대길이이내로절단(self):
        history = []
        for i in range(30):
            history += [_text("user", i), _text("assistant", i)]

        result = truncate_history(history)

        assert len(result) <= MAX_HISTORY
        assert result[-1] == history[-1]

    def test_절단경계가_toolresult면_쌍전체제거(self):
        # 앞쪽을 채우고 절단 경계에 tool_use/tool_result 쌍이 걸리게 구성
        history = []
        for i in range(MAX_HISTORY // 2):
            history += [_text("user", i), _text("assistant", i)]
        history += _tool_use_pair(99)  # 길이 42 → 절단 시 [-40:]의 첫 요소가 잘릴 위치
        history += [_text("user", 100), _text("assistant", 100)]

        result = truncate_history(history)

        # 첫 메시지는 반드시 일반 텍스트 user 메시지여야 한다 (고아 tool_result 금지)
        assert result[0]["role"] == "user"
        assert isinstance(result[0]["content"], str)

    def test_절단후_고아toolresult_없음(self):
        history = [_text("user", 0)]
        for i in range(MAX_HISTORY):
            history += _tool_use_pair(i)

        result = truncate_history(history)

        for idx, msg in enumerate(result):
            if isinstance(msg["content"], list) and any(
                b.get("type") == "tool_result" for b in msg["content"]
            ):
                prev = result[idx - 1] if idx > 0 else None
                assert prev is not None, "tool_result가 맨 앞에 남으면 안 된다"
                assert any(b.get("type") == "tool_use" for b in prev["content"])


class TestSummonAgent:
    HEADERS = {"X-Internal-Secret": "test-secret"}

    @pytest.fixture(autouse=True)
    def clear_sessions(self):
        internal._sessions.clear()
        yield
        internal._sessions.clear()

    def test_시크릿없음_401(self):
        client = TestClient(app)

        resp = client.post("/internal/agent/sessions", json={"roomId": "1"})

        assert resp.status_code in (401, 422)

    def test_소환_성공시_좌표와역할반환(self):
        client = TestClient(app)

        resp = client.post(
            "/internal/agent/sessions",
            json={"roomId": "1", "role": "helper", "x": 700.0, "y": 200.0},
            headers=self.HEADERS,
        )

        assert resp.status_code == 200
        data = resp.json()
        assert data["role"] == "helper"
        assert data["x"] == 700.0
        assert data["nickname"]

    def test_정원초과_409(self):
        client = TestClient(app)
        for _ in range(4):
            assert (
                client.post(
                    "/internal/agent/sessions",
                    json={"roomId": "1", "role": "helper"},
                    headers=self.HEADERS,
                ).status_code
                == 200
            )

        resp = client.post(
            "/internal/agent/sessions",
            json={"roomId": "1", "role": "helper"},
            headers=self.HEADERS,
        )

        assert resp.status_code == 409

    def test_동일역할_중복소환시_닉네임에_번호부여(self):
        client = TestClient(app)
        first = client.post(
            "/internal/agent/sessions",
            json={"roomId": "1", "role": "helper"},
            headers=self.HEADERS,
        ).json()

        second = client.post(
            "/internal/agent/sessions",
            json={"roomId": "1", "role": "helper"},
            headers=self.HEADERS,
        ).json()

        assert first["nickname"] != second["nickname"]
        assert second["nickname"].endswith("2")

    def test_퇴장후_재소환시_닉네임_중복없음(self):
        client = TestClient(app)
        first = client.post(
            "/internal/agent/sessions",
            json={"roomId": "1", "role": "helper"},
            headers=self.HEADERS,
        ).json()
        second = client.post(
            "/internal/agent/sessions",
            json={"roomId": "1", "role": "helper"},
            headers=self.HEADERS,
        ).json()

        # 1번("AI 도우미") 퇴장 후 재소환 → 기존 "AI 도우미 2"와 중복되면 안 된다
        client.delete(f"/internal/agent/sessions/{first['agentId']}", headers=self.HEADERS)
        third = client.post(
            "/internal/agent/sessions",
            json={"roomId": "1", "role": "helper"},
            headers=self.HEADERS,
        ).json()

        assert third["nickname"] != second["nickname"]

    def test_다른방은_정원에영향없음(self):
        client = TestClient(app)
        for _ in range(4):
            client.post(
                "/internal/agent/sessions",
                json={"roomId": "1", "role": "helper"},
                headers=self.HEADERS,
            )

        resp = client.post(
            "/internal/agent/sessions",
            json={"roomId": "2", "role": "helper"},
            headers=self.HEADERS,
        )

        assert resp.status_code == 200
