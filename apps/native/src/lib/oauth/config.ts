import type { OAuthProviderInfo } from "@zula/oauth"
import Constants from "expo-constants"
import * as AuthSession from "expo-auth-session"

export type NativeClientIds = {
  webClientId: string
  iosClientId?: string
  androidClientId?: string
}

function readEnv(name: string): string | undefined {
  const value = process.env[name]?.trim()
  return value ? value : undefined
}

const providerClientIdEnv: Record<
  string,
  { web?: string; ios?: string; android?: string; fallback?: string }
> = {
  google: {
    web: "EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID",
    ios: "EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID",
    android: "EXPO_PUBLIC_GOOGLE_ANDROID_CLIENT_ID",
    fallback: "EXPO_PUBLIC_GOOGLE_CLIENT_ID",
  },
  apple: {
    fallback: "EXPO_PUBLIC_APPLE_CLIENT_ID",
  },
}

/** App scheme used for OAuth redirect (must match app.json `expo.scheme`). */
export function getNativeOAuthScheme(): string {
  return Constants.expoConfig?.scheme?.toString() || "zula"
}

/**
 * Redirect URI for Expo AuthSession.
 * Register the same value in Google Cloud Console → Authorized redirect URIs:
 *   zula://oauth
 */
export function getNativeOAuthRedirectUri(): string {
  const scheme = getNativeOAuthScheme()
  return AuthSession.makeRedirectUri({
    scheme,
    path: "oauth",
    // `path` is ignored for native; set explicit URI for Console registration.
    native: `${scheme}://oauth`,
  })
}

export function getNativeOAuthRedirectUriOptions() {
  const scheme = getNativeOAuthScheme()
  return {
    scheme,
    path: "oauth",
    native: `${scheme}://oauth`,
  }
}

/**
 * Resolve platform client IDs.
 * Prefer server `provider.clientId` (same Web client as web popup) unless Expo env overrides.
 */
export function resolveNativeClientIds(
  provider: OAuthProviderInfo,
  overrides?: Partial<NativeClientIds>
): NativeClientIds {
  const env = providerClientIdEnv[provider.id]
  const webClientId =
    overrides?.webClientId ??
    (env?.web ? readEnv(env.web) : undefined) ??
    (env?.fallback ? readEnv(env.fallback) : undefined) ??
    provider.clientId.trim()

  return {
    webClientId,
    iosClientId:
      overrides?.iosClientId ?? (env?.ios ? readEnv(env.ios) : undefined),
    androidClientId:
      overrides?.androidClientId ?? (env?.android ? readEnv(env.android) : undefined),
  }
}

export function createNativeDiscovery(provider: OAuthProviderInfo) {
  return {
    authorizationEndpoint: provider.authorizeUrl,
    tokenEndpoint: provider.tokenUrl,
  }
}
