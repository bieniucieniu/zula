import { useQueryClient } from "@tanstack/react-query"
import {
  getGetSessionQueryKey,
  getSession,
  refresh,
  useGetSession,
} from "@zula/api/endpoints"
import type { SessionResponse } from "@zula/api"
import { createContext, use, type ReactNode } from "react"

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

  const remote = sessionQuery.isSuccess ? sessionQuery.data.data : null
  const session = remote ? toSession(remote) : null

  return (
    <AuthContext value={{ session, ready: !sessionQuery.isLoading }}>{children}</AuthContext>
  )
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
