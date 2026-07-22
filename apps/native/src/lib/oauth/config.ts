import type { OAuthProviderInfo } from "@zula/oauth"

export type NativeClientIds = {
  webClientId: string
  iosClientId?: string
  androidClientId?: string
}

function readEnv(name: string): string | undefined {
  return process.env[name]
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

export function resolveNativeClientIds(
  provider: OAuthProviderInfo,
  overrides?: Partial<NativeClientIds>
): NativeClientIds {
  const env = providerClientIdEnv[provider.id]
  const fallback =
    overrides?.webClientId ??
    (env?.web ? readEnv(env.web) : undefined) ??
    (env?.fallback ? readEnv(env.fallback) : undefined) ??
    provider.clientId

  return {
    webClientId: fallback,
    iosClientId:
      overrides?.iosClientId ?? (env?.ios ? readEnv(env.ios) : undefined) ?? fallback,
    androidClientId:
      overrides?.androidClientId ?? (env?.android ? readEnv(env.android) : undefined) ?? fallback,
  }
}

export function createNativeDiscovery(provider: OAuthProviderInfo) {
  return {
    authorizationEndpoint: provider.authorizeUrl,
    tokenEndpoint: provider.tokenUrl,
  }
}
