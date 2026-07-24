import type { OAuthProviderInfo, OAuthSignInResult } from "@zula/oauth"
import type { AuthSessionResult, AuthRequest } from "expo-auth-session"
import { getNativeOAuthRedirectUri } from "./config"

export function toOAuthSignInResult(
  provider: OAuthProviderInfo,
  result: AuthSessionResult,
  request?: AuthRequest | null
): OAuthSignInResult {
  if (result.type === "cancel" || result.type === "dismiss") {
    throw new Error("OAuth sign-in cancelled")
  }

  if (result.type === "error") {
    throw new Error(result.error?.message ?? result.params?.error ?? "OAuth sign-in failed")
  }

  if (result.type !== "success") {
    throw new Error(`OAuth sign-in failed (${result.type})`)
  }

  const code = result.params?.code
  if (code) {
    if (!request?.codeVerifier) {
      throw new Error("OAuth response missing code verifier")
    }
    return {
      provider: provider.id,
      code,
      codeVerifier: request.codeVerifier,
      redirectUri: request.redirectUri ?? getNativeOAuthRedirectUri(),
    }
  }

  const idToken = result.params?.id_token ?? result.authentication?.idToken ?? null
  if (!idToken) {
    throw new Error("OAuth response missing authorization code")
  }

  return {
    provider: provider.id,
    idToken,
    accessToken: result.authentication?.accessToken ?? result.params?.access_token ?? null,
    refreshToken: result.authentication?.refreshToken ?? result.params?.refresh_token ?? null,
  }
}
