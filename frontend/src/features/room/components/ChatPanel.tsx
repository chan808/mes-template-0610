"use client";

import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import { useChatStore } from "../stores/chatStore";
import { useComposerStore } from "../stores/composerStore";
import { useMessageHistory } from "../hooks/useMessages";
import { ClientMessage } from "../types/ws";

interface ChatPanelProps {
  roomId: number;
  myUserId: number;
  onSend: (msg: ClientMessage) => void;
}

export default function ChatPanel({ roomId, myUserId, onSend }: ChatPanelProps) {
  const { messages, hasMore } = useChatStore();
  const { target, clearTarget } = useComposerStore();
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

    const isNearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 120;
    if (!lastMessageIdRef.current || isNearBottom) {
      bottomRef.current?.scrollIntoView({ behavior: "smooth" });
    }
    lastMessageIdRef.current = last.id;
  }, [messages]);

  function handleSend() {
    const content = input.trim();
    if (!content) return;
    if (target?.kind === "user") {
      // 귓속말: 타겟에게만 전송 (서버가 발신자에게 에코)
      onSend({ type: "whisper", targetUserId: target.userId, content });
    } else if (target?.kind === "agent") {
      // 에이전트 타겟: 기존 @멘션 라우팅 재사용 (대화는 방 전체 공개)
      onSend({ type: "chat", content: `@${target.nickname} ${content}` });
    } else {
      onSend({ type: "chat", content });
    }
    setInput("");
  }

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
    if (e.key === "Escape") {
      clearTarget();
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

          if (msg.type === "whisper") {
            const isMine = msg.fromUserId === myUserId;
            return (
              <div key={msg.id} className={`flex flex-col gap-0.5 ${isMine ? "items-end" : "items-start"}`}>
                <span className="px-1 text-xs font-medium text-fuchsia-400">
                  {isMine ? `귓속말 → ${msg.toNickname}` : `귓속말 ← ${msg.nickname}`}
                </span>
                <div className={`flex items-end gap-1 ${isMine ? "flex-row-reverse" : "flex-row"}`}>
                  <div
                    className={`max-w-[200px] rounded-2xl border border-dashed border-fuchsia-500/60 bg-fuchsia-950/50 px-3 py-2 text-sm italic break-words text-fuchsia-100 ${
                      isMine ? "rounded-br-sm" : "rounded-bl-sm"
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
          }

          if (msg.type === "agent") {
            return (
              <div key={msg.id} className="flex flex-col gap-0.5 items-start">
                <span className="px-1 text-xs font-medium text-violet-400">{msg.nickname}</span>
                <div className="flex items-end gap-1 flex-row">
                  <div className="max-w-[200px] rounded-2xl rounded-bl-sm bg-violet-950 border border-violet-800 px-3 py-2 text-sm break-words text-violet-100">
                    {msg.content}
                    {msg.streaming && <span className="ml-1 inline-block animate-pulse">▋</span>}
                  </div>
                  {!msg.streaming && (
                    <span className="shrink-0 text-[10px] text-muted-foreground">
                      {formatTime(msg.createdAt)}
                    </span>
                  )}
                </div>
              </div>
            );
          }

          if (msg.type === "file") {
            return (
              <div key={msg.id} className="flex flex-col gap-0.5 items-start">
                <span className="px-1 text-xs font-medium text-violet-400">{msg.nickname}</span>
                <a
                  href={msg.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center gap-2 rounded-xl border border-violet-700 bg-violet-950 px-3 py-2 text-sm text-violet-100 hover:bg-violet-900 transition-colors"
                >
                  <span className="text-base">📄</span>
                  <span className="truncate max-w-[150px]">{msg.filename}</span>
                  <span className="shrink-0 text-xs text-violet-400">↓</span>
                </a>
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
      <div className="border-t border-border p-3 flex flex-col gap-2">
        {/* 타겟 칩: 더블클릭으로 설정된 귓속말/에이전트 타겟 표시, Esc 또는 ✕로 해제 */}
        {target && (
          <div className="flex items-center gap-2">
            <span
              className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${
                target.kind === "user"
                  ? "bg-fuchsia-950/60 text-fuchsia-300 border border-fuchsia-700"
                  : "bg-violet-950/60 text-violet-300 border border-violet-700"
              }`}
            >
              {target.kind === "user" ? `${target.nickname}에게 귓속말` : `@${target.nickname}에게만`}
              <button
                onClick={clearTarget}
                aria-label="타겟 해제"
                className="rounded-full hover:opacity-70"
              >
                ✕
              </button>
            </span>
            <span className="text-[10px] text-muted-foreground">Esc로 해제</span>
          </div>
        )}
        <div className="flex gap-2">
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={target?.kind === "user" ? `${target.nickname}에게 귓속말...` : "메시지를 입력하세요..."}
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
    </div>
  );
}
