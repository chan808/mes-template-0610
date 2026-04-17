"use client";

import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import { useChatStore } from "../stores/chatStore";
import { useMessageHistory } from "../hooks/useMessages";
import { ClientMessage } from "../types/ws";

interface ChatPanelProps {
  roomId: number;
  myUserId: number;
  onSend: (msg: ClientMessage) => void;
}

export default function ChatPanel({ roomId, myUserId, onSend }: ChatPanelProps) {
  const { messages, hasMore } = useChatStore();
  const { loadMore, isFetching } = useMessageHistory(roomId);
  const [input, setInput] = useState("");
  const bottomRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const lastMessageIdRef = useRef<string | null>(null);

  // 히스토리 prepend가 아닌 신규 메시지 append일 때만 하단으로 스크롤
  useEffect(() => {
    const last = messages[messages.length - 1];
    if (!last) return;
    if (last.id === lastMessageIdRef.current) return;

    const el = listRef.current;
    if (!el) return;

    // 기존 마지막 메시지 id 기준 — 처음 렌더 or 하단 근처면 스크롤
    const isNearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 120;
    if (!lastMessageIdRef.current || isNearBottom) {
      bottomRef.current?.scrollIntoView({ behavior: "smooth" });
    }
    lastMessageIdRef.current = last.id;
  }, [messages]);

  function handleSend() {
    const content = input.trim();
    if (!content) return;
    onSend({ type: "chat", content });
    setInput("");
  }

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }

  function formatTime(iso: string) {
    return new Date(iso).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
  }

  return (
    <div className="flex h-full flex-col rounded-xl border border-border bg-card">
      <div className="border-b border-border px-4 py-3">
        <h2 className="text-sm font-semibold">채팅</h2>
      </div>

      {/* 메시지 목록 */}
      <div ref={listRef} className="flex-1 overflow-y-auto px-4 py-3 space-y-2">
        {hasMore && (
          <button
            onClick={loadMore}
            disabled={isFetching}
            className="w-full rounded py-1 text-xs text-muted-foreground hover:text-foreground disabled:opacity-50"
          >
            {isFetching ? "불러오는 중..." : "이전 메시지 보기"}
          </button>
        )}

        {messages.map((msg) => {
          if (msg.type === "system") {
            return (
              <div key={msg.id} className="flex justify-center">
                <span className="rounded-full bg-muted px-3 py-0.5 text-xs text-muted-foreground">
                  {msg.content}
                </span>
              </div>
            );
          }

          const isMe = msg.userId === myUserId;

          return (
            <div key={msg.id} className={`flex flex-col gap-0.5 ${isMe ? "items-end" : "items-start"}`}>
              {!isMe && (
                <span className="px-1 text-xs font-medium text-muted-foreground">
                  {msg.nickname}
                </span>
              )}
              <div className={`flex items-end gap-1 ${isMe ? "flex-row-reverse" : "flex-row"}`}>
                <div
                  className={`max-w-[200px] rounded-2xl px-3 py-2 text-sm break-words ${
                    isMe
                      ? "rounded-br-sm bg-primary text-primary-foreground"
                      : "rounded-bl-sm bg-muted text-foreground"
                  }`}
                >
                  {msg.content}
                </div>
                <span className="shrink-0 text-[10px] text-muted-foreground">
                  {formatTime(msg.createdAt)}
                </span>
              </div>
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>

      {/* 입력창 */}
      <div className="border-t border-border p-3 flex gap-2">
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="메시지를 입력하세요..."
          maxLength={500}
          className="flex-1 rounded-lg border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
        />
        <button
          onClick={handleSend}
          disabled={!input.trim()}
          className="rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-50"
        >
          전송
        </button>
      </div>
    </div>
  );
}
