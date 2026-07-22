import { getProviderDefinition } from "../providers/registry"
import type { OAuthProviderInfo } from "../types"

export type NativeClientIds = {
  webClientId: string
  iosClientId?: string
  androidClientId?: string
}

function readEnv(name?: string): string | undefined {
  if (!name) return undefined
  return process.env[name]
}

export function resolveNativeClientIds(
  provider: OAuthProviderInfo,
  overrides?: Partial<NativeClientIds>
): NativeClientIds {
  const definition = getProviderDefinition(provider.id)
  const env = definition?.native?.clientIdEnv

  const fallback =
    overrides?.webClientId ??
    readEnv(env?.web) ??
    readEnv(env?.fallback) ??
    provider.clientId

  return {
    webClientId: fallback,
    iosClientId: overrides?.iosClientId ?? readEnv(env?.ios) ?? fallback,
    androidClientId: overrides?.androidClientId ?? readEnv(env?.android) ?? fallback,
  }
}

export function createNativeDiscovery(provider: OAuthProviderInfo) {
  return {
    authorizationEndpoint: provider.authorizeUrl,
    tokenEndpoint: provider.tokenUrl,
  }
}
