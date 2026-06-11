"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";
import { messageApi, HistoryMessage } from "../api/messageApi";
import { useChatStore } from "../stores/chatStore";
import { DisplayMessage } from "../types/ws";

function toDisplayMessages(messages: HistoryMessage[]): DisplayMessage[] {
  return messages.map((m): DisplayMessage => {
    if (m.type === "agent") {
      return {
        id: String(m.id),
        type: "agent",
        // 히스토리에는 세션 agentId가 없음 (표시에는 닉네임만 사용)
        agentId: "",
        nickname: m.agentNickname ?? "AI",
        content: m.content,
        createdAt: m.createdAt,
        streaming: false,
      };
    }
    if (m.type === "system") {
      return { id: String(m.id), type: "system", content: m.content, createdAt: m.createdAt };
    }
    return {
      id: String(m.id),
      type: "chat",
      userId: m.userId ?? 0,
      nickname: m.nickname ?? "시스템",
      content: m.content,
      createdAt: m.createdAt,
    };
  });
}

export function useMessageHistory(roomId: number) {
  const { prependHistory, hasMore, oldestCursor } = useChatStore();
  const [isFetching, setIsFetching] = useState(false);

  useEffect(() => {
    messageApi
      .getMessages(roomId)
      .then((res) => {
        const result = res.data.data!;
        const messages = toDisplayMessages(result.messages);
        // DESC 정렬이므로 마지막 요소가 가장 오래된 메시지 → cursor로 사용
        const cursor = result.messages.length > 0
          ? result.messages[result.messages.length - 1].id
          : null;
        prependHistory(messages, result.hasMore, cursor);
      })
      .catch(() => toast.error("채팅 기록을 불러오지 못했습니다."));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId]);

  function loadMore() {
    if (!hasMore || !oldestCursor || isFetching) return;
    setIsFetching(true);
    messageApi
      .getMessages(roomId, { before: oldestCursor })
      .then((res) => {
        const result = res.data.data!;
        const messages = toDisplayMessages(result.messages);
        const cursor = result.messages.length > 0
          ? result.messages[result.messages.length - 1].id
          : null;
        prependHistory(messages, result.hasMore, cursor);
      })
      .catch(() => toast.error("이전 메시지를 불러오지 못했습니다."))
      .finally(() => setIsFetching(false));
  }

  return { isFetching, loadMore };
}
