"use client";

import { useEffect, useRef, useCallback } from "react";
import { toast } from "sonner";
import { useAuthStore } from "@/features/auth/stores/authStore";
import { useWsStore } from "../stores/wsStore";
import { usePresenceStore } from "../stores/presenceStore";
import { useChatStore } from "../stores/chatStore";
import { ClientMessage, ServerMessage } from "../types/ws";

const WS_BASE_URL = process.env.NEXT_PUBLIC_WS_URL ?? "ws://localhost:8081";
const PING_INTERVAL_MS = 20_000;
const RECONNECT_DELAYS = [1000, 2000, 4000, 8000, 16000];

export function useWebSocket(roomId: number, myUserId: number) {
  const wsRef = useRef<WebSocket | null>(null);
  const pingTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const retryCountRef = useRef(0);
  const unmountedRef = useRef(false);

  const { setStatus, setWs, setHumanInputRequest, reset } = useWsStore();
  const { upsertPresence, removePresence, adoptServerPosition, cacheNickname, getNickname, upsertAgent, removeAgent, clear: clearPresence } = usePresenceStore();
  const { appendMessage, appendAgentChunk, clear: clearChat } = useChatStore();

  const handleMessage = useCallback(
    (event: MessageEvent) => {
      let msg: ServerMessage;
      try {
        msg = JSON.parse(event.data as string) as ServerMessage;
      } catch {
        return;
      }

      switch (msg.type) {
        case "presence":
          // 내 presence는 에코/서버 보정으로 처리 (이동 거부·재접속 시 권위 위치로 스냅)
          if (msg.userId === myUserId) {
            adoptServerPosition(msg.x, msg.y, msg.dir);
            break;
          }
          upsertPresence({
            userId: msg.userId,
            x: msg.x,
            y: msg.y,
            dir: msg.dir,
            nickname: msg.nickname,
            avatarId: msg.avatarId,
          });
          break;

        case "chat":
          appendMessage({
            id: String(msg.messageId),
            type: "chat",
            userId: msg.userId,
            // nicknameCache에서 조회 — presence 제거 후에도 닉네임 유지
            nickname: getNickname(msg.userId),
            content: msg.content,
            createdAt: msg.createdAt,
          });
          break;

        case "join":
          cacheNickname(msg.userId, msg.nickname);
          appendMessage({
            id: `join-${msg.userId}-${Date.now()}`,
            type: "system",
            content: `${msg.nickname}님이 입장했습니다.`,
            createdAt: new Date().toISOString(),
          });
          break;

        case "leave": {
          const nickname = getNickname(msg.userId);
          appendMessage({
            id: `leave-${msg.userId}-${Date.now()}`,
            type: "system",
            content: `${nickname}님이 퇴장했습니다.`,
            createdAt: new Date().toISOString(),
          });
          removePresence(msg.userId);
          break;
        }

        case "agent_joined":
          upsertAgent({ agentId: msg.agentId, role: msg.role, nickname: msg.nickname, x: msg.x, y: msg.y });
          appendMessage({
            id: `agent-join-${msg.agentId}-${Date.now()}`,
            type: "system",
            content: `${msg.nickname}이(가) 소환되었습니다.`,
            createdAt: new Date().toISOString(),
          });
          break;

        case "agent_left": {
          const agent = usePresenceStore.getState().agents.get(msg.agentId);
          const agentName = agent?.nickname ?? "AI 에이전트";
          removeAgent(msg.agentId);
          // HitL 다이얼로그가 열려있던 에이전트가 퇴장하면 닫기
          const current = useWsStore.getState().humanInputRequest;
          if (current?.agentId === msg.agentId) {
            setHumanInputRequest(null);
          }
          appendMessage({
            id: `agent-leave-${msg.agentId}-${Date.now()}`,
            type: "system",
            content: `${agentName}이(가) 퇴장했습니다.`,
            createdAt: new Date().toISOString(),
          });
          break;
        }

        case "agent_message": {
          const agent = usePresenceStore.getState().agents.get(msg.agentId);
          appendAgentChunk(msg.agentId, agent?.nickname ?? "AI", msg.content, msg.done);
          break;
        }

        case "agent_needs_input": {
          const agent = usePresenceStore.getState().agents.get(msg.agentId);
          setHumanInputRequest({
            agentId: msg.agentId,
            toolUseId: msg.toolUseId,
            agentNickname: agent?.nickname ?? "AI 에이전트",
            prompt: msg.prompt,
            options: msg.options ?? [],
          });
          break;
        }

        case "agent_thinking": {
          const agent = usePresenceStore.getState().agents.get(msg.agentId);
          appendMessage({
            id: `agent-thinking-${msg.agentId}-${Date.now()}`,
            type: "system",
            content: `${agent?.nickname ?? "AI"}: ${msg.step}`,
            createdAt: new Date().toISOString(),
          });
          break;
        }

        case "agent_file": {
          const agent = usePresenceStore.getState().agents.get(msg.agentId);
          appendMessage({
            id: `agent-file-${msg.agentId}-${Date.now()}`,
            type: "file",
            agentId: msg.agentId,
            nickname: agent?.nickname ?? "AI",
            filename: msg.filename,
            url: msg.url,
            mimeType: msg.mimeType,
            createdAt: new Date().toISOString(),
          });
          break;
        }

        case "error":
          toast.error(`서버 오류: ${msg.message}`);
          break;
      }
    },
    [myUserId, upsertPresence, removePresence, adoptServerPosition, cacheNickname, getNickname, appendMessage, appendAgentChunk, upsertAgent, removeAgent, setHumanInputRequest],
  );

  const startPing = useCallback((ws: WebSocket) => {
    pingTimerRef.current = setInterval(() => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: "ping" }));
      }
    }, PING_INTERVAL_MS);
  }, []);

  const stopPing = useCallback(() => {
    if (pingTimerRef.current) {
      clearInterval(pingTimerRef.current);
      pingTimerRef.current = null;
    }
  }, []);

  const connect = useCallback(() => {
    if (unmountedRef.current) return;

    const token = useAuthStore.getState().accessToken;
    if (!token) {
      toast.error("인증 정보가 없습니다. 다시 로그인해주세요.");
      return;
    }

    setStatus("connecting");
    const ws = new WebSocket(`${WS_BASE_URL}/ws/rooms/${roomId}?token=${token}`);
    wsRef.current = ws;
    setWs(ws);

    ws.onopen = () => {
      if (unmountedRef.current) {
        ws.close();
        return;
      }
      retryCountRef.current = 0;
      setStatus("connected");
      startPing(ws);
    };

    ws.onmessage = handleMessage;

    ws.onerror = () => {
      setStatus("error");
    };

    ws.onclose = () => {
      stopPing();
      // 이미 교체된 이전 연결의 close 이벤트는 무시 (StrictMode 이중 마운트 방어)
      if (unmountedRef.current || wsRef.current !== ws) return;

      setStatus("disconnected");
      setWs(null);

      const delay = RECONNECT_DELAYS[Math.min(retryCountRef.current, RECONNECT_DELAYS.length - 1)];
      retryCountRef.current += 1;

      if (retryCountRef.current === 1) {
        toast.warning("연결이 끊겼습니다. 재연결 중...");
      }
      reconnectTimerRef.current = setTimeout(connect, delay);
    };
  }, [roomId, handleMessage, startPing, stopPing, setStatus, setWs]);

  const disconnect = useCallback(() => {
    unmountedRef.current = true;
    stopPing();
    if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
    wsRef.current?.close();
    wsRef.current = null;
    reset();
  }, [stopPing, reset]);

  const send = useCallback((msg: ClientMessage) => {
    const ws = wsRef.current;
    if (ws?.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(msg));
    }
  }, []);

  useEffect(() => {
    unmountedRef.current = false;
    clearPresence();
    clearChat();
    connect();

    return () => {
      disconnect();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId]);

  return { send };
}
