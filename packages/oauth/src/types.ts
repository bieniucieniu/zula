export type OAuthProviderInfo = {
  id: string
  clientId: string
  authorizeUrl: string
  tokenUrl: string
  scopes: string[]
}

export type OAuthSignInResult = {
  provider: string
  idToken: string
  accessToken?: string | null
  refreshToken?: string | null
}

export type OAuthSignInError = {
  provider: string
  code: string
  message: string
  cause?: unknown
}

export type OAuthWebStrategy = "google-identity" | "popup"

export type OAuthNativeStrategy = "expo-id-token"

export type OAuthProviderDefinition = {
  id: string
  label: string
  web?: {
    strategy: OAuthWebStrategy
    scriptUrl?: string
    extraAuthParams?: Record<string, string>
  }
  native?: {
    strategy: OAuthNativeStrategy
    clientIdEnv?: {
      web?: string
      ios?: string
      android?: string
      fallback?: string
    }
  }
}

export type BuildAuthorizeUrlInput = {
  provider: OAuthProviderInfo
  redirectUri: string
  state: string
  nonce: string
  responseType?: "id_token" | "code"
  extraParams?: Record<string, string>
}
