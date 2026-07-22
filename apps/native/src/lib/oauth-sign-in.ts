import { useListProviders } from "@zula/api/endpoints"
import { getProviderDefinition } from "@zula/oauth"
import { useCallback } from "react"
import { useAuth } from "@/lib/auth"
import { useNativeOAuthSignIn } from "@/lib/oauth/use-native-oauth-sign-in"

export function useProviderOAuthSignIn(providerId: string) {
  const { signInWithIdToken } = useAuth()
  const { data: providers, isLoading } = useListProviders()
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
  const provider = providers?.data.providers.find((p) => p.id === providerId)

  const oauth = useNativeOAuthSignIn({
    provider,
    onSuccess,
    onError: (error) => {
      console.error(`${providerId} sign-in failed`, error)
    },
  })

  return {
    label: definition?.label ?? providerId,
    ready: !isLoading && oauth.ready,
    loading: isLoading,
    pending: oauth.pending,
    signIn: oauth.signIn,
  }
}
