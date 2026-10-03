/**
 * OTP login wire calls against /api/laundry/auth/* (proxied to the shared
 * backend by next.config rewrites). Port of data/remote/AuthApi.kt — success
 * and error responses share the same shape; `code` carries the server's
 * stable error code.
 */

const BASE = "/api/laundry";

export interface AuthUserDto {
  id: string;
  phone: string;
  name?: string | null;
}

export interface OtpChallengeResponse {
  success: boolean;
  challengeId?: string;
  challengeToken?: string;
  expiresAt?: string;
  nextSendAt?: string;
  attemptsRemaining?: number;
  code?: string;
  message?: string;
  retryAt?: string;
}

export interface CredentialsResponse {
  success: boolean;
  user?: AuthUserDto;
  accessToken?: string;
  accessExpiresAt?: string;
  refreshToken?: string;
  sessionExpiresAt?: string;
  code?: string;
  message?: string;
  attemptsRemaining?: number;
}

/** Parsed response + HTTP status, so callers can distinguish 401 from offline. */
export interface ApiReply<T> {
  status: number;
  body: T | null;
}

/** Server ISO timestamp -> epoch ms; 0 if unparseable. */
export function parseIsoMs(iso?: string | null): number {
  if (!iso) return 0;
  const t = Date.parse(iso);
  return Number.isNaN(t) ? 0 : t;
}

async function post<T>(path: string, body: unknown, bearerToken?: string): Promise<ApiReply<T>> {
  const res = await fetch(`${BASE}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(bearerToken ? { Authorization: `Bearer ${bearerToken}` } : {}),
    },
    body: JSON.stringify(body),
  });
  let parsed: T | null = null;
  try {
    parsed = (await res.json()) as T;
  } catch {
    parsed = null;
  }
  return { status: res.status, body: parsed };
}

export const authApi = {
  requestOtp(phone: string) {
    return post<OtpChallengeResponse>("/auth/request-otp", { phone });
  },

  verifyOtp(challengeId: string, challengeToken: string, otp: string, deviceId: string) {
    return post<CredentialsResponse>("/auth/verify-otp", { challengeId, otp, deviceId }, challengeToken);
  },

  refresh(refreshToken: string) {
    return post<CredentialsResponse>("/auth/refresh", { refreshToken });
  },

  async logout(accessToken: string): Promise<number> {
    const res = await fetch(`${BASE}/auth/logout`, {
      method: "POST",
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    return res.status;
  },
};
