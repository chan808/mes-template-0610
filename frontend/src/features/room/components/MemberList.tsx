"use client";

import { usePresenceStore } from "../stores/presenceStore";
import { useWsStore } from "../stores/wsStore";
import { AgentRole, ClientMessage } from "../types/ws";

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

const AGENT_ROLES: { role: AgentRole; label: string }[] = [
  { role: "helper", label: "도우미" },
  { role: "summarizer", label: "요약자" },
  { role: "researcher", label: "조사원" },
  { role: "critic", label: "검토자" },
  { role: "orchestrator", label: "코디네이터" },
];

const MAX_AGENTS = 4;

export default function MemberList({ myUserId, myNickname, onSend }: MemberListProps) {
  const { presences, agents } = usePresenceStore();
  const { status } = useWsStore();

  const others = [...presences.values()].filter((p) => p.userId !== myUserId);
  const agentList = [...agents.values()];
  const canSummon = agentList.length < MAX_AGENTS;

  function handleSummon(role: AgentRole) {
    onSend({ type: "summon_agent", role });
  }

  function handleDismiss(agentId: string) {
    onSend({ type: "dismiss_agent", agentId });
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

        {/* 에이전트 항목 (개별 퇴장 버튼 포함) */}
        {agentList.map((a) => (
          <li key={a.agentId} className="flex items-center justify-between gap-2">
            <div className="flex min-w-0 items-center gap-2">
              <span className="h-2 w-2 shrink-0 rounded-full bg-violet-500" />
              <span className="truncate text-sm text-violet-400">{a.nickname}</span>
            </div>
            <button
              onClick={() => handleDismiss(a.agentId)}
              className="shrink-0 rounded px-1.5 py-0.5 text-[10px] text-violet-500 hover:bg-violet-500/10 transition-colors"
              title="에이전트 퇴장"
            >
              ✕
            </button>
          </li>
        ))}
      </ul>

      {/* 에이전트 소환 패널 */}
      <div className="mt-4 border-t border-border pt-3">
        {canSummon ? (
          <div className="flex flex-col gap-1.5">
            <p className="text-xs text-muted-foreground">
              AI 에이전트 소환
              {agentList.length > 0 && (
                <span className="ml-1 text-violet-500">({agentList.length}/{MAX_AGENTS})</span>
              )}
            </p>
            <div className="grid grid-cols-2 gap-1.5">
              {AGENT_ROLES.map(({ role, label }) => (
                <button
                  key={role}
                  onClick={() => handleSummon(role)}
                  className={`rounded-lg px-2 py-1.5 text-xs font-medium transition-colors ${
                    role === "orchestrator"
                      ? "col-span-2 bg-violet-600 text-white hover:bg-violet-700"
                      : "border border-violet-500/50 text-violet-400 hover:bg-violet-500/10"
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <p className="text-center text-xs text-muted-foreground">
            에이전트 최대 {MAX_AGENTS}개 소환됨
          </p>
        )}
      </div>
    </div>
  );
}
