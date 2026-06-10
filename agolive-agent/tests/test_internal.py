import os

# settings 로드 전에 테스트용 환경변수 주입
os.environ.setdefault("ANTHROPIC_API_KEY", "test-key")
os.environ.setdefault("INTERNAL_SECRET", "test-secret")

import pytest
from fastapi.testclient import TestClient

from main import app
from routers import internal
from routers.internal import MAX_HISTORY, truncate_history


def _text(role: str, i: int) -> dict:
    return {"role": role, "content": f"메시지 {i}"}


def _tool_use_pair(i: int) -> list[dict]:
    return [
        {"role": "assistant", "content": [{"type": "tool_use", "id": f"tu_{i}", "name": "t", "input": {}}]},
        {"role": "user", "content": [{"type": "tool_result", "tool_use_id": f"tu_{i}", "content": "ok"}]},
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
