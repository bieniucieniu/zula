import { useQueryClient } from "@tanstack/react-query"
import type { SessionResponse } from "@zula/api"
import { getGetSessionQueryKey, useGetSession } from "@zula/api/endpoints"
import { createContext, type ReactNode, use, useEffect, useState } from "react"
import "@/lib/api"
import { readStoredSession, writeStoredSession } from "@/lib/session-storage"
import { setAccessToken } from "@zula/api"

export type AuthSession = {
  email: string
  expiresIn?: number
}

type AuthContextValue = {
  session: AuthSession | null
  ready: boolean
  refreshing: boolean
  refresh: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

function toSession(remote: SessionResponse): AuthSession {
  return {
    email: remote.email ?? "",
    expiresIn: remote.expiresIn,
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [bootstrapped, setBootstrapped] = useState(false)

  useEffect(() => {
    void (async () => {
      const stored = await readStoredSession()
      if (stored) {
        setAccessToken(stored.accessToken)
      }
      setBootstrapped(true)
    })()
  }, [])

  const sessionQuery = useGetSession({
    query: {
      enabled: bootstrapped,
      retry: false,
      refetchInterval: 10 * 60 * 1000,
      refetchOnWindowFocus: true,
    },
  })

  useEffect(() => {
    if (sessionQuery.isError) {
      void writeStoredSession(null)
      queryClient.setQueryData(getGetSessionQueryKey(), null)
    }
  }, [sessionQuery.isError, queryClient])

  const remote = sessionQuery.data?.data
  const session = remote && !sessionQuery.isError ? toSession(remote) : null

  return (
    <AuthContext
      value={{
        session,
        refreshing: sessionQuery.isFetching,
        ready: bootstrapped && !sessionQuery.isLoading,
        refresh: async () => {
          await sessionQuery.refetch()
        },
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
