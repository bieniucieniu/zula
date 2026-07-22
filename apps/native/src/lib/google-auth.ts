import { useListProviders } from "@zula/api/endpoints"
import * as Google from "expo-auth-session/providers/google"
import * as WebBrowser from "expo-web-browser"
import { useEffect, useMemo } from "react"
import { Platform } from "react-native"
import { useAuth } from "@/lib/auth"

WebBrowser.maybeCompleteAuthSession()

function googleClientIds(providerClientId?: string | null) {
  return {
    webClientId:
      process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID ??
      process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID ??
      providerClientId ??
      "",
    iosClientId:
      process.env.EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID ??
      process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID ??
      providerClientId ??
      "",
    androidClientId:
      process.env.EXPO_PUBLIC_GOOGLE_ANDROID_CLIENT_ID ??
      process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID ??
      providerClientId ??
      "",
  }
}

export function useGoogleSignIn() {
  const { signInWithIdToken } = useAuth()
  const providersQuery = useListProviders()
  const google = providersQuery.data?.data.providers.find((provider) => provider.id === "google")
  const clientIds = useMemo(() => googleClientIds(google?.clientId), [google?.clientId])

  const [request, response, promptAsync] = Google.useIdTokenAuthRequest({
    clientId: clientIds.webClientId,
    iosClientId: clientIds.iosClientId,
    androidClientId: clientIds.androidClientId,
  })

  useEffect(() => {
    if (response?.type !== "success") return
    const idToken = response.params.id_token ?? response.authentication?.idToken
    if (!idToken) return

    void signInWithIdToken({
      provider: "google",
      idToken,
    })
  }, [response, signInWithIdToken])

  return {
    ready: Boolean(request) && Boolean(clientIds.webClientId),
    loading: providersQuery.isLoading,
    promptAsync,
    platform: Platform.OS,
  }
}
