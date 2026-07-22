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

export type OAuthProviderDefinition = {
  id: string
  label: string
  extraAuthParams?: Record<string, string>
}

export type BuildAuthorizeUrlInput = {
  provider: OAuthProviderInfo
  redirectUri: string
  state: string
  nonce: string
  responseType?: "id_token" | "code"
  extraParams?: Record<string, string>
}

export type OAuthSignInExecutor = (provider: OAuthProviderInfo) => Promise<OAuthSignInResult>
