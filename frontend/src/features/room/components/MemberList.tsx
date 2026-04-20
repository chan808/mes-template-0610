"use client";

import { usePresenceStore } from "../stores/presenceStore";
import { useWsStore } from "../stores/wsStore";

interface MemberListProps {
  myUserId: number;
  myNickname: string;
}

const STATUS_COLORS: Record<string, string> = {
  connected: "bg-green-500",
  connecting: "bg-yellow-500",
  disconnected: "bg-gray-400",
  error: "bg-red-500",
};

export default function MemberList({ myUserId, myNickname }: MemberListProps) {
  const { presences } = usePresenceStore();
  const { status } = useWsStore();

  const others = [...presences.values()].filter((p) => p.userId !== myUserId);

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
      </ul>
    </div>
  );
}
