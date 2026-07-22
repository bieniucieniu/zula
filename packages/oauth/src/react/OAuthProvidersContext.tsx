import { createContext, use, type ReactNode } from "react"
import type { OAuthProviderInfo } from "../types"

type OAuthProvidersContextValue = {
  providers: OAuthProviderInfo[]
  getProvider: (id: string) => OAuthProviderInfo | undefined
}

const OAuthProvidersContext = createContext<OAuthProvidersContextValue | null>(null)

export function OAuthProvidersProvider({
  providers,
  children,
}: {
  providers: OAuthProviderInfo[]
  children: ReactNode
}) {
  const value: OAuthProvidersContextValue = {
    providers,
    getProvider: (id) => providers.find((provider) => provider.id === id),
  }

  return <OAuthProvidersContext value={value}>{children}</OAuthProvidersContext>
}

export function useOAuthProviders() {
  const ctx = use(OAuthProvidersContext)
  if (!ctx) throw new Error("useOAuthProviders must be used within OAuthProvidersProvider")
  return ctx
}

export function useOAuthProvider(id: string) {
  const { getProvider } = useOAuthProviders()
  return getProvider(id)
}
