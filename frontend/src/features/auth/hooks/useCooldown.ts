"use client";

import { useEffect, useState } from "react";

export function useCooldown(initialSeconds = 0) {
  const [secondsLeft, setSecondsLeft] = useState(initialSeconds);

  useEffect(() => {
    if (secondsLeft <= 0) return;

    const timer = window.setInterval(() => {
      setSecondsLeft((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);

    return () => window.clearInterval(timer);
  }, [secondsLeft]);

  return {
    secondsLeft,
    startCooldown: (seconds: number) => setSecondsLeft(seconds),
    isCoolingDown: secondsLeft > 0,
  };
}
