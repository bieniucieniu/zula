import AsyncStorage from "@react-native-async-storage/async-storage"
import {
  authenticateWithIdToken,
  setAccessToken,
  type StoredOAuthSession,
} from "@zula/api"
import { getSession, logout as apiLogout } from "@zula/api/endpoints"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { createContext, use, useCallback, useEffect, useMemo, useState, type ReactNode } from "react"
import { configureApiClient } from "@/lib/api"
import { setPowerSyncAccessToken } from "@/lib/powersync"

const SESSION_STORAGE_KEY = "zula.oauth.session"

configureApiClient()

export type AuthSession = {
  email: string
  expiresIn?: number
}

type AuthContextValue = {
  session: AuthSession | null
  ready: boolean
  signInWithIdToken: (input: {
    provider: string
    idToken: string
    providerRefreshToken?: string | null
  }) => Promise<void>
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

async function readStoredSession(): Promise<StoredOAuthSession | null> {
  const raw = await AsyncStorage.getItem(SESSION_STORAGE_KEY)
  if (!raw) return null
  return JSON.parse(raw) as StoredOAuthSession
}

async function writeStoredSession(session: StoredOAuthSession | null) {
  if (!session) {
    await AsyncStorage.removeItem(SESSION_STORAGE_KEY)
    setAccessToken(null)
    setPowerSyncAccessToken(null)
    return
  }

  await AsyncStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session))
  setAccessToken(session.accessToken)
  setPowerSyncAccessToken(session.accessToken)
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [bootstrapped, setBootstrapped] = useState(false)

  useEffect(() => {
    void (async () => {
      const stored = await readStoredSession()
      if (stored) {
        setAccessToken(stored.accessToken)
        setPowerSyncAccessToken(stored.accessToken)
      }
      setBootstrapped(true)
    })()
  }, [])

  const sessionQuery = useQuery({
    queryKey: ["auth", "session"],
    enabled: bootstrapped,
    retry: false,
    queryFn: async () => {
      const { data } = await getSession()
      return data
    },
  })

  const session = useMemo<AuthSession | null>(() => {
    if (!sessionQuery.data || sessionQuery.isError) return null
    return {
      email: sessionQuery.data.email ?? "",
      expiresIn: sessionQuery.data.expiresIn,
    }
  }, [sessionQuery.data, sessionQuery.isError])

  const signInWithIdToken = useCallback(
    async (input: {
      provider: string
      idToken: string
      providerRefreshToken?: string | null
    }) => {
      const stored = await readStoredSession()
      const next = await authenticateWithIdToken({
        provider: input.provider,
        idToken: input.idToken,
        providerRefreshToken: input.providerRefreshToken,
        sessionId: stored?.sessionId,
        deviceInfo: "native",
      })
      await writeStoredSession(next)
      await queryClient.invalidateQueries({ queryKey: ["auth", "session"] })
    },
    [queryClient]
  )

  const signOut = useCallback(async () => {
    try {
      await apiLogout({})
    } catch {
      // clear local session even if server logout fails
    }
    await writeStoredSession(null)
    await queryClient.resetQueries({ queryKey: ["auth", "session"] })
  }, [queryClient])

  const ready = bootstrapped && !sessionQuery.isPending

  return (
    <AuthContext
      value={{
        session,
        ready,
        signInWithIdToken,
        signOut,
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
