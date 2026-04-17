"use client";

import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { messageApi } from "../api/messageApi";
import { useChatStore } from "../stores/chatStore";
import { DisplayMessage } from "../types/ws";

export function useMessageHistory(roomId: number) {
  const { prependHistory, oldestCursor } = useChatStore();

  const { data, isFetching, refetch } = useQuery({
    queryKey: ["messages", roomId, "history", oldestCursor],
    queryFn: () =>
      messageApi
        .getMessages(roomId, { before: oldestCursor ?? undefined })
        .then((res) => res.data.data!),
    enabled: false,
  });

  // 초기 히스토리 로드
  useEffect(() => {
    messageApi
      .getMessages(roomId)
      .then((res) => {
        const result = res.data.data!;
        const messages: DisplayMessage[] = result.messages.map((m) => ({
          id: String(m.id),
          type: m.type,
          userId: m.userId ?? 0,
          nickname: m.nickname ?? "시스템",
          content: m.content,
          createdAt: m.createdAt,
        }));
        const cursor = result.messages.length > 0 ? result.messages[0].id : null;
        prependHistory(messages, result.hasMore, cursor);
      })
      .catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId]);

  function loadMore() {
    if (!oldestCursor) return;
    messageApi
      .getMessages(roomId, { before: oldestCursor })
      .then((res) => {
        const result = res.data.data!;
        const messages: DisplayMessage[] = result.messages.map((m) => ({
          id: String(m.id),
          type: m.type,
          userId: m.userId ?? 0,
          nickname: m.nickname ?? "시스템",
          content: m.content,
          createdAt: m.createdAt,
        }));
        const cursor = result.messages.length > 0 ? result.messages[0].id : null;
        prependHistory(messages, result.hasMore, cursor);
      })
      .catch(() => {});
  }

  return { isFetching, loadMore, data, refetch };
}
