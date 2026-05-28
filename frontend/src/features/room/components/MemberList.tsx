"use client";

import { usePresenceStore } from "../stores/presenceStore";
import { useWsStore } from "../stores/wsStore";
import { ClientMessage } from "../types/ws";

interface MemberListProps {
  myUserId: number;
  myNickname: string;
  onSend: (msg: ClientMessage) => void;
}

const STATUS_COLORS: Record<string, string> = {
  connected: "bg-green-500",
  connecting: "bg-yellow-500",
  disconnected: "bg-gray-400",
  error: "bg-red-500",
};

export default function MemberList({ myUserId, myNickname, onSend }: MemberListProps) {
  const { presences, agents } = usePresenceStore();
  const { status } = useWsStore();

  const others = [...presences.values()].filter((p) => p.userId !== myUserId);
  const agentList = [...agents.values()];
  const hasAgent = agentList.length > 0;

  function handleSummon(role: "helper" | "summarizer") {
    onSend({ type: "summon_agent", role });
  }

  function handleDismiss() {
    onSend({ type: "dismiss_agent" });
  }

  return (
    <div className="rounded-xl border border-border bg-card p-4">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-semibold">접속자</h2>
        <div className="flex items-center gap-1.5">
          <span className={`h-2 w-2 rounded-full ${STATUS_COLORS[status]}`} />
          <span className="text-xs text-muted-foreground">{others.length + 1}명</span>
        </div>
      </div>

      <ul className="space-y-2">
        {/* 내 항목 */}
        <li className="flex items-center gap-2">
          <span className="h-2 w-2 shrink-0 rounded-full bg-green-500" />
          <span className="truncate text-sm text-foreground">
            {myNickname}
            <span className="ml-1 text-xs text-muted-foreground">(나)</span>
          </span>
        </li>

        {others.map((p) => (
          <li key={p.userId} className="flex items-center gap-2">
            <span className="h-2 w-2 shrink-0 rounded-full bg-green-500" />
            <span className="truncate text-sm text-foreground">{p.nickname}</span>
          </li>
        ))}

        {/* 에이전트 항목 */}
        {agentList.map((a) => (
          <li key={a.agentId} className="flex items-center gap-2">
            <span className="h-2 w-2 shrink-0 rounded-full bg-violet-500" />
            <span className="truncate text-sm text-violet-400">{a.nickname}</span>
          </li>
        ))}
      </ul>

      {/* 에이전트 소환/해제 */}
      <div className="mt-4 border-t border-border pt-3">
        {hasAgent ? (
          <button
            onClick={handleDismiss}
            className="w-full rounded-lg border border-violet-500/50 px-3 py-1.5 text-xs text-violet-400 hover:bg-violet-500/10 transition-colors"
          >
            에이전트 퇴장
          </button>
        ) : (
          <div className="flex flex-col gap-1.5">
            <p className="text-xs text-muted-foreground">AI 에이전트 소환</p>
            <div className="flex gap-1.5">
              <button
                onClick={() => handleSummon("helper")}
                className="flex-1 rounded-lg bg-violet-600 px-2 py-1.5 text-xs font-medium text-white hover:bg-violet-700 transition-colors"
              >
                도우미
              </button>
              <button
                onClick={() => handleSummon("summarizer")}
                className="flex-1 rounded-lg border border-violet-500/50 px-2 py-1.5 text-xs text-violet-400 hover:bg-violet-500/10 transition-colors"
              >
                요약자
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
