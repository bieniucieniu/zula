import { getSession, refresh } from "@zula/api/endpoints"
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
  setSession: (session: AuthSession | null) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

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
    const { data } = await getSession()
    return data
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSessionState] = useState<AuthSession | null>(null)
  const [ready, setReady] = useState(false)

  function setSession(next: AuthSession | null) {
    writeStoredSession(next)
    setSessionState(next)
  }

  useEffect(() => {
    let cancelled = false

    async function bootstrap() {
      const stored = readStoredSession()
      if (stored && !cancelled) setSessionState(stored)

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
          setSessionState(next)
          return
        }

        writeStoredSession(null)
        setSessionState(null)
      } catch {
        if (!cancelled) {
          writeStoredSession(null)
          setSessionState(null)
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

  return <AuthContext value={{ session, ready, setSession }}>{children}</AuthContext>
}

export function useAuth() {
  const ctx = use(AuthContext)
  if (!ctx) throw new Error("useAuth must be used within AuthProvider")
  return ctx
}
