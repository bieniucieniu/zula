import { useQueryClient } from "@tanstack/react-query"
import type { SessionResponse } from "@zula/api"
import { getGetSessionQueryKey, useGetSession } from "@zula/api/endpoints"
import { createContext, type ReactNode, use, useEffect } from "react"

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
  const sessionQuery = useGetSession({
    query: {
      retry: false,
      placeholderData: undefined,
      refetchInterval: 10 * 60 * 1000,
      refetchIntervalInBackground: true,
      refetchOnWindowFocus: true,
    },
  })

  useEffect(() => {
    if (sessionQuery.isError) {
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
        ready: !sessionQuery.isLoading,
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
