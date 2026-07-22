import { getProviderDefinition } from "@zula/oauth"
import { useOAuthProvider } from "@zula/oauth/react"
import { useCallback } from "react"
import { useAuth } from "@/lib/auth"
import { useNativeOAuthSignIn } from "@/lib/oauth/use-native-oauth-sign-in"

export function useProviderOAuthSignIn(providerId: string) {
  const { signInWithIdToken } = useAuth()
  const { provider, isLoading, ready: providerReady } = useOAuthProvider(providerId)
  const definition = getProviderDefinition(providerId)

  const onSuccess = useCallback(
    async (result: { provider: string; idToken: string; refreshToken?: string | null }) => {
      await signInWithIdToken({
        provider: result.provider,
        idToken: result.idToken,
        providerRefreshToken: result.refreshToken,
      })
    },
    [signInWithIdToken]
  )

  const oauth = useNativeOAuthSignIn({
    provider,
    onSuccess,
    onError: (error) => {
      console.error(`${providerId} sign-in failed`, error)
    },
  })

  return {
    label: definition?.label ?? providerId,
    ready: providerReady && oauth.ready,
    loading: isLoading,
    pending: oauth.pending,
    signIn: oauth.signIn,
  }
}
