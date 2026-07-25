import { useCallback, useEffect } from "react"
import { Platform } from "react-native"
import type { OAuthProviderInfo, OAuthSignInResult } from "@zula/oauth"
import { getProviderDefinition } from "@zula/oauth"
import { useOAuthSignIn } from "@zula/oauth/react"
import { createNativeDiscovery, getNativeOAuthRedirectUri, resolveNativeClientIds } from "./config"
import { toOAuthSignInResult } from "./sign-in"

type AuthSessionModule = typeof import("expo-auth-session")
type WebBrowserModule = typeof import("expo-web-browser")

let webBrowserReady = false

async function ensureWebBrowserReady() {
  if (webBrowserReady) return
  const WebBrowser: WebBrowserModule = await import("expo-web-browser")
  WebBrowser.maybeCompleteAuthSession()
  webBrowserReady = true
}

function pickPlatformClientId(clientIds: {
  webClientId: string
  iosClientId?: string
  androidClientId?: string
}): string {
  if (Platform.OS === "ios") {
    return clientIds.iosClientId ?? clientIds.webClientId
  }
  if (Platform.OS === "android") {
    return clientIds.androidClientId ?? clientIds.webClientId
  }
  return clientIds.webClientId
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
  const clientIds = provider ? resolveNativeClientIds(provider) : null
  const clientId = clientIds ? pickPlatformClientId(clientIds) : null
  const redirectUri = getNativeOAuthRedirectUri()
  const discovery = provider ? createNativeDiscovery(provider) : null
  const extraParams = provider ? getProviderDefinition(provider.id)?.extraAuthParams : undefined

  const AuthSession: AuthSessionModule = require("expo-auth-session")
  const [request, , promptAsync] = AuthSession.useAuthRequest(
    provider && clientId
      ? {
          clientId,
          scopes: provider.scopes,
          responseType: AuthSession.ResponseType.Code,
          redirectUri,
          usePKCE: true,
          extraParams,
        }
      : {
          clientId: "disabled",
          redirectUri,
        },
    discovery ?? {
      authorizationEndpoint: "https://invalid.local/oauth/authorize",
      tokenEndpoint: "https://invalid.local/oauth/token",
    }
  )

  useEffect(() => {
    void ensureWebBrowserReady()
  }, [])

  const signInExecutor = useCallback(
    async ({ provider: activeProvider }: { provider: OAuthProviderInfo }) => {
      await ensureWebBrowserReady()
      if (!promptAsync || !request) {
        throw new Error("OAuth request not ready")
      }

      const result = await promptAsync()
      return toOAuthSignInResult(activeProvider, result, request)
    },
    [promptAsync, request]
  )

  return useOAuthSignIn({
    provider: provider && clientId && request ? provider : undefined,
    signIn: signInExecutor,
    onSuccess,
    onError,
  })
}
