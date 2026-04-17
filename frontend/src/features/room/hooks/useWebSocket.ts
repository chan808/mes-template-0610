"use client";

import { useEffect, useRef, useCallback } from "react";
import { useAuthStore } from "@/features/auth/stores/authStore";
import { useWsStore } from "../stores/wsStore";
import { usePresenceStore } from "../stores/presenceStore";
import { useChatStore } from "../stores/chatStore";
import { ClientMessage, ServerMessage } from "../types/ws";

const WS_BASE_URL = process.env.NEXT_PUBLIC_WS_URL ?? "ws://localhost:8081";
const PING_INTERVAL_MS = 20_000;
const RECONNECT_DELAYS = [1000, 2000, 4000, 8000, 16000];

export function useWebSocket(roomId: number) {
  const wsRef = useRef<WebSocket | null>(null);
  const pingTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const retryCountRef = useRef(0);
  const unmountedRef = useRef(false);

  const { setStatus, setWs, reset } = useWsStore();
  const { upsertPresence, removePresence, clear: clearPresence } = usePresenceStore();
  const { appendMessage, clear: clearChat } = useChatStore();

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
          upsertPresence({
            userId: msg.userId,
            x: msg.x,
            y: msg.y,
            nickname: msg.nickname,
            avatarId: msg.avatarId,
          });
          break;

        case "chat":
          appendMessage({
            id: String(msg.messageId),
            type: "chat",
            userId: msg.userId,
            // nickname은 presence에서 조회 (WS chat 이벤트에 nickname이 없으므로)
            nickname: usePresenceStore.getState().presences.get(msg.userId)?.nickname ?? "알 수 없음",
            content: msg.content,
            createdAt: msg.createdAt,
          });
          break;

        case "join":
          appendMessage({
            id: `join-${msg.userId}-${Date.now()}`,
            type: "system",
            content: `${msg.nickname}님이 입장했습니다.`,
            createdAt: new Date().toISOString(),
          });
          break;

        case "leave":
          appendMessage({
            id: `leave-${msg.userId}-${Date.now()}`,
            type: "system",
            content: `${usePresenceStore.getState().presences.get(msg.userId)?.nickname ?? "누군가"}님이 퇴장했습니다.`,
            createdAt: new Date().toISOString(),
          });
          removePresence(msg.userId);
          break;

        case "error":
          console.error(`[WS] 서버 에러: ${msg.code} - ${msg.message}`);
          break;
      }
    },
    [upsertPresence, removePresence, appendMessage],
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
    if (!token) return;

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
      if (unmountedRef.current) return;

      setStatus("disconnected");
      setWs(null);

      // 지수 백오프 재연결
      const delay = RECONNECT_DELAYS[Math.min(retryCountRef.current, RECONNECT_DELAYS.length - 1)];
      retryCountRef.current += 1;
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
