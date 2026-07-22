import { useQueryClient } from "@tanstack/react-query"
import type { SessionResponse } from "@zula/api"
import { getGetSessionQueryKey, useGetSession } from "@zula/api/endpoints"
import { createContext, type ReactNode, use } from "react"

export type AuthSession = SessionResponse

type AuthContextValue = {
  session: AuthSession | null
  ready: boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const sessionQuery = useGetSession({
    query: {
      retry: false,
    },
  })

  const session = sessionQuery.isSuccess ? sessionQuery.data.data : null

  return <AuthContext value={{ session, ready: !sessionQuery.isLoading }}>{children}</AuthContext>
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
