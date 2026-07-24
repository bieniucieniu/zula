import { authenticate } from "./generated/endpoints"
import type { AuthenticateRequest, AuthTokensResponse } from "./generated/model"

export type StoredOAuthSession = {
  accessToken: string
  expiresIn: number
  refreshToken?: string | null
  sessionId?: string | null
}

function base64UrlDecode(input: string): string {
  const padded = input.padEnd(input.length + ((4 - (input.length % 4)) % 4), "=")
  const base64 = padded.replace(/-/g, "+").replace(/_/g, "/")
  if (typeof globalThis.atob !== "function") {
    throw new Error("base64 decode unavailable")
  }
  return globalThis.atob(base64)
}

export function sessionIdFromAccessToken(accessToken: string): string | null {
  try {
    const payload = decodeJwtPayload(accessToken)
    const sid = payload.sid
    return typeof sid === "string" ? sid : null
  } catch {
    return null
  }
}

export function decodeJwtPayload(token: string): Record<string, unknown> {
  const part = token.split(".")[1]
  if (!part) throw new Error("Invalid JWT")
  return JSON.parse(base64UrlDecode(part)) as Record<string, unknown>
}

export function toStoredOAuthSession(tokens: AuthTokensResponse): StoredOAuthSession {
  return {
    accessToken: tokens.accessToken,
    expiresIn: tokens.expiresIn,
    refreshToken: tokens.refreshToken,
    sessionId: tokens.sessionId ?? sessionIdFromAccessToken(tokens.accessToken),
  }
}

export async function authenticateWithIdToken(input: {
  provider: string
  idToken: string
  providerRefreshToken?: string | null
  sessionId?: string | null
  deviceInfo?: string | null
  scopes?: string | null
}) {
  const body: AuthenticateRequest = {
    provider: input.provider,
    idToken: input.idToken,
    providerRefreshToken: input.providerRefreshToken,
    sessionId: input.sessionId,
    deviceInfo: input.deviceInfo,
    scopes: input.scopes,
  }
  const { data } = await authenticate(body)
  return toStoredOAuthSession(data)
}

/** Local-only bypass when server has AUTH_DEV_BYPASS_SECRET set. */
export async function authenticateWithDevBypass(input: {
  secret: string
  email?: string
  sessionId?: string | null
}) {
  const body: AuthenticateRequest = {
    provider: "dev",
    code: input.secret,
    deviceInfo: input.email,
    sessionId: input.sessionId,
  }
  const { data } = await authenticate(body)
  return toStoredOAuthSession(data)
}
