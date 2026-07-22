import type { OAuthProviderInfo, OAuthSignInResult } from "@zula/oauth"
import type { AuthSessionResult } from "expo-auth-session"

export function idTokenFromAuthSessionResult(result: AuthSessionResult): string | null {
  if (result.type !== "success") return null
  return result.params?.id_token ?? result.authentication?.idToken ?? null
}

export function toOAuthSignInResult(
  provider: OAuthProviderInfo,
  result: AuthSessionResult
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

  const idToken = idTokenFromAuthSessionResult(result)
  if (!idToken) {
    throw new Error("OAuth response missing id_token")
  }

  return {
    provider: provider.id,
    idToken,
    accessToken: result.authentication?.accessToken ?? result.params?.access_token ?? null,
    refreshToken: result.authentication?.refreshToken ?? result.params?.refresh_token ?? null,
  }
}
