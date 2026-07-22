import type { AuthTokensResponse } from "@zula/api"
import { setApiBaseUrl, setAccessTokenGetter } from "@zula/api"

const defaultApiUrl = "/api"

export type SessionResponse = {
  expiresIn: number
  email?: string | null
}

let accessToken: string | null = null

export function getAccessToken() {
  return accessToken
}

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function configureApiClient() {
  setApiBaseUrl(import.meta.env.VITE_API_URL ?? defaultApiUrl)
  setAccessTokenGetter(() => accessToken)
}

type ApiPayload<T> = T | { data: T }

function unwrapApiData<T>(response: ApiPayload<T>): T {
  if (response && typeof response === "object" && "data" in response) {
    return response.data
  }
  return response as T
}

export type PowerSyncTokenResponse = {
  accessToken: string
}

export async function fetchPowerSyncToken(): Promise<string | null> {
  const res = await fetch(`${import.meta.env.VITE_API_URL ?? defaultApiUrl}/auth/powersync/token`, {
    credentials: "include",
  })
  if (res.status === 401) return null
  if (!res.ok) {
    throw new Error(`PowerSync token request failed (${res.status})`)
  }
  const body = (await res.json()) as PowerSyncTokenResponse
  return body.accessToken
}

export async function fetchSession(): Promise<SessionResponse | null> {
  const res = await fetch(`${import.meta.env.VITE_API_URL ?? defaultApiUrl}/auth/session`, {
    credentials: "include",
  })
  if (res.status === 401) return null
  if (!res.ok) {
    throw new Error(`Session request failed (${res.status})`)
  }
  return (await res.json()) as SessionResponse
}

export function applyAuthTokens(tokens: AuthTokensResponse, email?: string | null) {
  setAccessToken(tokens.accessToken)
  return {
    email: email ?? "",
    expiresIn: tokens.expiresIn,
    refreshToken: tokens.refreshToken ?? undefined,
  }
}

export function unwrapTokens(response: ApiPayload<AuthTokensResponse>) {
  return unwrapApiData(response)
}
