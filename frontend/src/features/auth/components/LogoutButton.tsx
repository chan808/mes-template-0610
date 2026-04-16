"use client";

import { useAuth } from "../hooks/useAuth";
import { Button } from "@/shared/components/ui/button";

export default function LogoutButton() {
  const { logout } = useAuth();

  return (
    <Button variant="outline" onClick={() => logout()}>
      로그아웃
    </Button>
  );
}
