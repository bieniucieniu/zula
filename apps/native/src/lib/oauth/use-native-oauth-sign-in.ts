import type { OAuthProviderInfo, OAuthSignInResult } from "@zula/oauth"
import { useEffect, useMemo } from "react"
import { createNativeDiscovery, resolveNativeClientIds } from "./config"

type AuthSessionModule = typeof import("expo-auth-session")
type GoogleModule = typeof import("expo-auth-session/providers/google")
type WebBrowserModule = typeof import("expo-web-browser")

let webBrowserReady = false

async function ensureWebBrowserReady() {
  if (webBrowserReady) return
  const WebBrowser: WebBrowserModule = await import("expo-web-browser")
  WebBrowser.maybeCompleteAuthSession()
  webBrowserReady = true
}

export type UseNativeOAuthSignInOptions = {
  provider?: OAuthProviderInfo
  onSuccess?: (result: OAuthSignInResult) => void | Promise<void>
  onError?: (error: Error) => void
}

export function useNativeOAuthSignIn({
  provider,
  onSuccess,
  onError,
}: UseNativeOAuthSignInOptions) {
  const isGoogle = provider?.id === "google"
  const clientIds = useMemo(
    () => (provider ? resolveNativeClientIds(provider) : null),
    [provider]
  )

  const google = useGoogleIdTokenAuth(provider, clientIds, isGoogle)
  const generic = useGenericIdTokenAuth(provider, clientIds, !isGoogle)
  const active = isGoogle ? google : generic

  useEffect(() => {
    void ensureWebBrowserReady()
  }, [])

  useEffect(() => {
    const response = active.response
    if (!provider || !response || response.type !== "success") return

    const idToken = response.params?.id_token ?? response.authentication?.idToken
    if (!idToken) {
      onError?.(new Error("OAuth response missing id_token"))
      return
    }

    void onSuccess?.({
      provider: provider.id,
      idToken,
      accessToken: response.authentication?.accessToken ?? null,
      refreshToken: response.authentication?.refreshToken ?? null,
    })
  }, [active.response, provider, onSuccess, onError])

  return {
    ready: Boolean(active.request) && Boolean(clientIds?.webClientId),
    promptAsync: active.promptAsync,
    providerId: provider?.id,
  }
}

function useGoogleIdTokenAuth(
  provider: OAuthProviderInfo | undefined,
  clientIds: ReturnType<typeof resolveNativeClientIds> | null,
  enabled: boolean
) {
  const Google: GoogleModule = require("expo-auth-session/providers/google")
  const [request, response, promptAsync] = Google.useIdTokenAuthRequest(
    enabled && clientIds
      ? {
          clientId: clientIds.webClientId,
          iosClientId: clientIds.iosClientId,
          androidClientId: clientIds.androidClientId,
        }
      : {
          clientId: "disabled",
        }
  )

  return {
    request: enabled && provider ? request : null,
    response: enabled ? response : null,
    promptAsync,
  }
}

function useGenericIdTokenAuth(
  provider: OAuthProviderInfo | undefined,
  clientIds: ReturnType<typeof resolveNativeClientIds> | null,
  enabled: boolean
) {
  const AuthSession: AuthSessionModule = require("expo-auth-session")
  const discovery = provider ? createNativeDiscovery(provider) : null

  const [request, response, promptAsync] = AuthSession.useAuthRequest(
    enabled && provider && clientIds
      ? {
          clientId: clientIds.webClientId,
          scopes: provider.scopes,
          responseType: AuthSession.ResponseType.IdToken,
          redirectUri: AuthSession.makeRedirectUri(),
        }
      : {
          clientId: "disabled",
        },
    discovery ?? {
      authorizationEndpoint: "https://invalid.local/oauth/authorize",
      tokenEndpoint: "https://invalid.local/oauth/token",
    }
  )

  return {
    request: enabled && provider ? request : null,
    response: enabled ? response : null,
    promptAsync,
  }
}
