"use client";

import { useState } from "react";
import { useWsStore } from "../stores/wsStore";
import { ClientMessage } from "../types/ws";

interface HumanInLoopDialogProps {
  onSend: (msg: ClientMessage) => void;
}

export default function HumanInLoopDialog({ onSend }: HumanInLoopDialogProps) {
  const { humanInputRequest, setHumanInputRequest } = useWsStore();
  const [customInput, setCustomInput] = useState("");

  if (!humanInputRequest) return null;

  const { agentId, toolUseId, agentNickname, prompt, options } = humanInputRequest;

  function respond(response: string) {
    if (!response.trim()) return;
    onSend({ type: "agent_input", agentId, response });
    setHumanInputRequest(null);
    setCustomInput("");
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter") respond(customInput);
    if (e.key === "Escape") {
      // 취소 시에도 빈 응답으로 에이전트 재개
      respond("(사용자가 응답하지 않았습니다)");
    }
  }

  // toolUseId는 서버로 전송하지 않아도 됨 — agentId만으로 식별
  void toolUseId;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
      <div className="w-full max-w-sm rounded-2xl border border-violet-700 bg-card p-5 shadow-2xl">
        {/* 헤더 */}
        <div className="mb-4 flex items-center gap-2">
          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-violet-600 text-xs font-bold text-white">
            AI
          </span>
          <span className="text-sm font-semibold text-violet-300">{agentNickname}</span>
        </div>

        {/* 질문 */}
        <p className="mb-4 text-sm text-foreground">{prompt}</p>

        {/* 선택지 버튼 */}
        {options.length > 0 && (
          <div className="mb-3 flex flex-col gap-2">
            {options.map((opt) => (
              <button
                key={opt}
                onClick={() => respond(opt)}
                className="w-full rounded-lg border border-violet-500/50 px-3 py-2 text-left text-sm text-violet-200 hover:bg-violet-500/20 transition-colors"
              >
                {opt}
              </button>
            ))}
          </div>
        )}

        {/* 자유 입력 */}
        <div className="flex gap-2">
          <input
            autoFocus
            type="text"
            value={customInput}
            onChange={(e) => setCustomInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={options.length > 0 ? "직접 입력..." : "응답을 입력하세요..."}
            className="flex-1 rounded-lg border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-violet-500"
          />
          <button
            onClick={() => respond(customInput)}
            disabled={!customInput.trim()}
            className="rounded-lg bg-violet-600 px-3 py-2 text-sm font-medium text-white hover:bg-violet-700 disabled:opacity-40 transition-colors"
          >
            전송
          </button>
        </div>

        <p className="mt-2 text-center text-[10px] text-muted-foreground">
          ESC — 응답 없이 계속
        </p>
      </div>
    </div>
  );
}
