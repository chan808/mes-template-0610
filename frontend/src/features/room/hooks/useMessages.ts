"use client";

import { useEffect, useState } from "react";
import { messageApi } from "../api/messageApi";
import { useChatStore } from "../stores/chatStore";
import { DisplayMessage } from "../types/ws";

function toDisplayMessages(messages: Awaited<ReturnType<typeof messageApi.getMessages>>["data"]["data"]): DisplayMessage[] {
  return (messages?.messages ?? []).map((m) => ({
    id: String(m.id),
    type: m.type,
    userId: m.userId ?? 0,
    nickname: m.nickname ?? "시스템",
    content: m.content,
    createdAt: m.createdAt,
  }));
}

export function useMessageHistory(roomId: number) {
  const { prependHistory, hasMore, oldestCursor } = useChatStore();
  const [isFetching, setIsFetching] = useState(false);

  // 초기 히스토리 로드
  useEffect(() => {
    messageApi
      .getMessages(roomId)
      .then((res) => {
        const result = res.data.data!;
        const messages = toDisplayMessages(res.data.data);
        // DESC 정렬이므로 마지막 요소가 가장 오래된 메시지 → cursor로 사용
        const cursor = result.messages.length > 0
          ? result.messages[result.messages.length - 1].id
          : null;
        prependHistory(messages, result.hasMore, cursor);
      })
      .catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId]);

  function loadMore() {
    if (!hasMore || !oldestCursor || isFetching) return;
    setIsFetching(true);
    messageApi
      .getMessages(roomId, { before: oldestCursor })
      .then((res) => {
        const result = res.data.data!;
        const messages = toDisplayMessages(res.data.data);
        const cursor = result.messages.length > 0
          ? result.messages[result.messages.length - 1].id
          : null;
        prependHistory(messages, result.hasMore, cursor);
      })
      .catch(() => {})
      .finally(() => setIsFetching(false));
  }

  return { isFetching, loadMore };
}
