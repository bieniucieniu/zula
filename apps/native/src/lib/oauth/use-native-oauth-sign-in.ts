import { useCallback, useEffect } from "react"
import type { OAuthProviderInfo, OAuthSignInResult } from "@zula/oauth"
import { getProviderDefinition } from "@zula/oauth"
import { useOAuthSignIn } from "@zula/oauth/react"
import {
  createNativeDiscovery,
  getNativeOAuthRedirectUri,
  getNativeOAuthRedirectUriOptions,
  resolveNativeClientIds,
} from "./config"
import { toOAuthSignInResult } from "./sign-in"

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
  const clientIds = provider ? resolveNativeClientIds(provider) : null
  const redirectUri = getNativeOAuthRedirectUri()

  const google = useGoogleIdTokenAuth(provider, clientIds, isGoogle)
  const generic = useGenericIdTokenAuth(provider, clientIds, redirectUri, !isGoogle)
  const active = isGoogle ? google : generic

  useEffect(() => {
    void ensureWebBrowserReady()
  }, [])

  const signInExecutor = useCallback(
    async (activeProvider: OAuthProviderInfo) => {
      await ensureWebBrowserReady()
      if (!active.promptAsync) {
        throw new Error("OAuth request not ready")
      }

      if (__DEV__) {
        console.info(`[zula] OAuth authorize ${activeProvider.id}`, {
          clientId: clientIds?.webClientId,
          iosClientId: clientIds?.iosClientId,
          androidClientId: clientIds?.androidClientId,
          redirectUri,
        })
      }

      const result = await active.promptAsync()
      return toOAuthSignInResult(activeProvider, result)
    },
    [active.promptAsync, clientIds, redirectUri]
  )

  return useOAuthSignIn({
    provider: active.ready ? provider : undefined,
    signIn: signInExecutor,
    onSuccess,
    onError,
  })
}

function useGoogleIdTokenAuth(
  provider: OAuthProviderInfo | undefined,
  clientIds: ReturnType<typeof resolveNativeClientIds> | null,
  enabled: boolean
) {
  const Google: GoogleModule = require("expo-auth-session/providers/google")
  const scopes = provider?.scopes ?? ["openid", "email", "profile"]

  const [request, , promptAsync] = Google.useIdTokenAuthRequest(
    enabled && clientIds
      ? {
          clientId: clientIds.webClientId,
          iosClientId: clientIds.iosClientId,
          androidClientId: clientIds.androidClientId,
          scopes,
          selectAccount: true,
        }
      : {
          clientId: "disabled",
        },
    getNativeOAuthRedirectUriOptions()
  )

  return {
    ready:
      enabled && Boolean(provider?.clientId) && Boolean(request) && Boolean(clientIds?.webClientId),
    promptAsync: enabled ? promptAsync : null,
  }
}

function useGenericIdTokenAuth(
  provider: OAuthProviderInfo | undefined,
  clientIds: ReturnType<typeof resolveNativeClientIds> | null,
  redirectUri: string,
  enabled: boolean
) {
  const AuthSession: AuthSessionModule = require("expo-auth-session")
  const discovery = provider ? createNativeDiscovery(provider) : null
  const extraParams = provider ? getProviderDefinition(provider.id)?.extraAuthParams : undefined

  const [request, , promptAsync] = AuthSession.useAuthRequest(
    enabled && provider && clientIds
      ? {
          clientId: clientIds.webClientId,
          scopes: provider.scopes,
          responseType: AuthSession.ResponseType.IdToken,
          redirectUri,
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

  return {
    ready:
      enabled && Boolean(provider?.clientId) && Boolean(request) && Boolean(clientIds?.webClientId),
    promptAsync: enabled ? promptAsync : null,
  }
}
