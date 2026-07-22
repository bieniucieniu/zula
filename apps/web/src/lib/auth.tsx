import { useQueryClient } from "@tanstack/react-query"
import { getGetSessionQueryKey, getSession, refresh, useGetSession } from "@zula/api/endpoints"
import type { SessionResponse } from "@zula/api"
import { createContext, use, useEffect, type ReactNode } from "react"

export type AuthSession = {
  email: string
  expiresIn?: number
}

type AuthContextValue = {
  session: AuthSession | null
  ready: boolean
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
      queryFn: async () => {
        try {
          return await getSession()
        } catch {
          await refresh({})
          return await getSession()
        }
      },
    },
  })

  useEffect(() => {
    if (sessionQuery.isError) {
      queryClient.setQueryData(getGetSessionQueryKey(), null)
    }
  }, [sessionQuery.isError, queryClient])

  const remote = sessionQuery.data?.data
  const session = remote && !sessionQuery.isError ? toSession(remote) : null
  const ready = !sessionQuery.isPending && !sessionQuery.isFetching

  // First load: ready once settled. Allow refetch after login while keeping prior session.
  const settled = !sessionQuery.isLoading

  return <AuthContext value={{ session, ready: settled }}>{children}</AuthContext>
}

export function useAuth() {
  const ctx = use(AuthContext)
  if (!ctx) throw new Error("useAuth must be used within AuthProvider")
  return ctx
}

export function useInvalidateSession() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: getGetSessionQueryKey() })
}
