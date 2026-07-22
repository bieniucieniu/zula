import {
  authenticate,
  createChallenge,
  getSession,
  logout as logoutRequest,
  refresh,
} from "@zula/api/endpoints"
import type { SessionResponse } from "@zula/api"
import { createContext, use, useEffect, useState, type ReactNode } from "react"

const SESSION_KEY = "app.auth.session"

export type AuthSession = {
  email: string
  expiresIn?: number
}

type AuthContextValue = {
  session: AuthSession | null
  ready: boolean
  requestEmailOtp: (email: string) => Promise<{ challengeId: string; devCode?: string }>
  verifyEmailOtp: (input: { email: string; challengeId: string; code: string }) => Promise<void>
  loginWithGoogle: () => void
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

function unwrapApiData<T>(response: T | { data: T }): T {
  if (response && typeof response === "object" && "data" in response) {
    return (response as { data: T }).data
  }
  return response as T
}

function readStoredSession(): AuthSession | null {
  if (typeof window === "undefined") return null
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as AuthSession
    if (!parsed?.email) return null
    return { email: parsed.email, expiresIn: parsed.expiresIn }
  } catch {
    return null
  }
}

function writeStoredSession(session: AuthSession | null) {
  if (typeof window === "undefined") return
  if (session) {
    sessionStorage.setItem(
      SESSION_KEY,
      JSON.stringify({ email: session.email, expiresIn: session.expiresIn })
    )
  } else {
    sessionStorage.removeItem(SESSION_KEY)
  }
}

function toSession(remote: SessionResponse, emailFallback = ""): AuthSession {
  return {
    email: remote.email ?? emailFallback,
    expiresIn: remote.expiresIn,
  }
}

async function loadRemoteSession(): Promise<SessionResponse | null> {
  try {
    return unwrapApiData(await getSession())
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    let cancelled = false

    async function bootstrap() {
      const stored = readStoredSession()
      if (stored && !cancelled) setSession(stored)

      try {
        let remote = await loadRemoteSession()

        if (!remote) {
          try {
            await refresh({})
            remote = await loadRemoteSession()
          } catch {
            remote = null
          }
        }

        if (cancelled) return

        if (remote) {
          const next = toSession(remote, stored?.email ?? "")
          writeStoredSession(next)
          setSession(next)
          return
        }

        writeStoredSession(null)
        setSession(null)
      } catch {
        if (!cancelled) {
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

    const tokens = unwrapApiData(
      await authenticate({
        provider: "email_otp",
        challengeId,
        code: trimmedCode,
      })
    )

    const next: AuthSession = {
      email: trimmed,
      expiresIn: tokens.expiresIn,
    }
    writeStoredSession(next)
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

export function useAuth() {
  const ctx = use(AuthContext)
  if (!ctx) throw new Error("useAuth must be used within AuthProvider")
  return ctx
}
