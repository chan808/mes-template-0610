export interface LoginRequest {
  email: string;
  password: string;
}

export interface SignupRequest {
  email: string;
  password: string;
}

export interface TokenResponse {
  accessToken: string;
}

export interface MemberResponse {
  id: number;
  email: string;
  nickname: string | null;
  /** null이면 로컬 계정, "GOOGLE" | "NAVER" | "KAKAO"이면 소셜 계정 */
  provider: string | null;
  role: string;
  createdAt: string;
}
