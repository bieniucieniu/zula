import { getProviderDefinition } from "@zula/oauth"
import { useOAuthProvider } from "@zula/oauth/react"
import { useCallback } from "react"
import { useAuth } from "@/lib/auth"
import { useNativeOAuthSignIn } from "@/lib/oauth/use-native-oauth-sign-in"

export function useProviderOAuthSignIn(providerId: string) {
  const { signInWithIdToken } = useAuth()
  const { provider, isLoading, ready } = useOAuthProvider(providerId)
  const definition = getProviderDefinition(providerId)

  const onSuccess = useCallback(
    async (result: { provider: string; idToken: string }) => {
      await signInWithIdToken({
        provider: result.provider,
        idToken: result.idToken,
      })
    },
    [signInWithIdToken]
  )

  const oauth = useNativeOAuthSignIn({
    provider,
    onSuccess,
  })

  return {
    label: definition?.label ?? providerId,
    ready: ready && oauth.ready,
    loading: isLoading,
    promptAsync: oauth.promptAsync,
  }
}
