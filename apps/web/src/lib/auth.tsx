import {
  authenticate,
  createChallenge,
  logout as logoutRequest,
  refresh,
  type AuthTokensResponse,
} from "@zula/api/endpoints"
import {
  applyAuthTokens,
  configureApiClient,
  fetchSession,
  setAccessToken,
  unwrapTokens,
} from "@/lib/api-client"
import { createContext, use, useEffect, useState, type ReactNode } from "react"

const SESSION_KEY = "app.auth.session"

export type AuthSession = {
  email: string
  expiresIn?: number
  refreshToken?: string
}

type AuthContextValue = {
  session: AuthSession | null
  ready: boolean
  requestEmailOtp: (email: string) => Promise<{ challengeId: string; devCode?: string }>
  verifyEmailOtp: (input: {
    email: string
    challengeId: string
    code: string
  }) => Promise<void>
  loginWithGoogle: () => void
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

function readStoredSession(): AuthSession | null {
  if (typeof window === "undefined") return null
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as AuthSession
    if (!parsed?.email) return null
    return parsed
  } catch {
    return null
  }
}

function writeStoredSession(session: AuthSession | null) {
  if (typeof window === "undefined") return
  if (session) {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
  } else {
    sessionStorage.removeItem(SESSION_KEY)
  }
}

function toSession(tokens: AuthTokensResponse, email: string): AuthSession {
  return applyAuthTokens(tokens, email)
}

function adoptSession(session: AuthSession) {
  writeStoredSession(session)
}

configureApiClient()

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    let cancelled = false

    async function bootstrap() {
      const stored = readStoredSession()
      if (stored) {
        adoptSession(stored)
        if (!cancelled) setSession(stored)
      }

      try {
        const remote = await fetchSession()
        if (cancelled) return

        if (remote) {
          const next: AuthSession = {
            email: remote.email ?? stored?.email ?? "",
            expiresIn: remote.expiresIn,
            refreshToken: stored?.refreshToken,
          }
          adoptSession(next)
          setSession(next)
          return
        }

        if (stored?.refreshToken) {
          const refreshed = unwrapTokens(
            await refresh({ refreshToken: stored.refreshToken })
          )
          const next = toSession(refreshed, stored.email)
          adoptSession(next)
          setSession(next)
          return
        }

        setAccessToken(null)
        writeStoredSession(null)
        setSession(null)
      } catch {
        if (stored?.refreshToken) {
          try {
            const refreshed = unwrapTokens(
              await refresh({ refreshToken: stored.refreshToken })
            )
            const next = toSession(refreshed, stored.email)
            adoptSession(next)
            if (!cancelled) setSession(next)
            return
          } catch {
            // fall through to clear session
          }
        }

        if (!cancelled) {
          setAccessToken(null)
          writeStoredSession(null)
          setSession(null)
        }
      } finally {
        if (!cancelled) setReady(true)
      }
    }

    void bootstrap()

    return () => {
      cancelled = true
    }
  }, [])

  async function requestEmailOtp(email: string) {
    const trimmed = email.trim()
    if (!trimmed) throw new Error("Email required")

    const response = unwrapApiData(
      await createChallenge({
        channel: "email",
        target: trimmed,
        purpose: "login",
      })
    )

    return {
      challengeId: response.challengeId,
      devCode: response.token ?? undefined,
    }
  }

  async function verifyEmailOtp({
    email,
    challengeId,
    code,
  }: {
    email: string
    challengeId: string
    code: string
  }) {
    const trimmed = email.trim()
    const trimmedCode = code.trim()
    if (!trimmed || !challengeId || !trimmedCode) {
      throw new Error("Email and verification code required")
    }

    const tokens = unwrapTokens(
      await authenticate({
        provider: "email_otp",
        challengeId,
        code: trimmedCode,
      })
    )

    const next = toSession(tokens, trimmed)
    adoptSession(next)
    setSession(next)
  }

  function loginWithGoogle() {
    window.location.assign("/api/auth/login/google")
  }

  async function logout() {
    try {
      await logoutRequest(undefined)
    } catch {
      // Clear local session even if server logout fails.
    }
    setAccessToken(null)
    writeStoredSession(null)
    setSession(null)
  }

  return (
    <AuthContext
      value={{
        session,
        ready,
        requestEmailOtp,
        verifyEmailOtp,
        loginWithGoogle,
        logout,
      }}
    >
      {children}
    </AuthContext>
  )
}

function unwrapApiData<T>(response: T | { data: T }): T {
  if (response && typeof response === "object" && "data" in response) {
    return (response as { data: T }).data
  }
  return response as T
}

export function useAuth() {
  const ctx = use(AuthContext)
  if (!ctx) throw new Error("useAuth must be used within AuthProvider")
  return ctx
}
