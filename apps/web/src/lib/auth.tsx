import {
  createContext,
  use,
  useEffect,
  useState,
  type ReactNode,
} from "react"

const SESSION_KEY = "zula.auth.session"

export type AuthSession = {
  email: string
}

type AuthContextValue = {
  session: AuthSession | null
  ready: boolean
  login: (input: { email: string; password: string }) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

function readSession(): AuthSession | null {
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

function writeSession(session: AuthSession | null) {
  if (typeof window === "undefined") return
  if (session) {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
  } else {
    sessionStorage.removeItem(SESSION_KEY)
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    setSession(readSession())
    setReady(true)
  }, [])

  async function login({
    email,
    password,
  }: {
    email: string
    password: string
  }) {
    if (!email || !password) {
      throw new Error("Email and password required")
    }
    const next = { email }
    writeSession(next)
    setSession(next)
  }

  async function logout() {
    writeSession(null)
    setSession(null)
  }

  return (
    <AuthContext
      value={{
        session,
        ready,
        login,
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
