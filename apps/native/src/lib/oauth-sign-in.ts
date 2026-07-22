import { useListProviders } from "@zula/api/endpoints"
import { getProviderDefinition } from "@zula/oauth"
import { useNativeOAuthSignIn } from "@zula/oauth/native"
import { useCallback } from "react"
import { useAuth } from "@/lib/auth"

export function useProviderOAuthSignIn(providerId: string) {
  const { signInWithIdToken } = useAuth()
  const providersQuery = useListProviders()
  const provider = providersQuery.data?.data.providers.find((item) => item.id === providerId)
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
    ready: oauth.ready,
    loading: providersQuery.isLoading,
    promptAsync: oauth.promptAsync,
  }
}
