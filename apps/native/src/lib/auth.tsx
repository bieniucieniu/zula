import { useQueryClient } from "@tanstack/react-query"
import type { SessionResponse } from "@zula/api"
import { getGetSessionQueryKey, useGetSession } from "@zula/api/endpoints"
import { createContext, type ReactNode, use, useEffect, useState } from "react"
import { configureNativeApi } from "@/lib/api"
import { loadTokens } from "@/lib/token-store"

configureNativeApi()

export type AuthSession = SessionResponse

type AuthContextValue = {
  session: AuthSession | null
  ready: boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [tokensReady, setTokensReady] = useState(false)

  useEffect(() => {
    void loadTokens().finally(() => setTokensReady(true))
  }, [])

  const sessionQuery = useGetSession({
    query: {
      retry: false,
      enabled: tokensReady,
    },
  })

  const session = sessionQuery.isSuccess ? sessionQuery.data.data : null
  const ready = tokensReady && !sessionQuery.isLoading

  return <AuthContext value={{ session, ready }}>{children}</AuthContext>
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
