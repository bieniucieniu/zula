import { authenticateWithIdToken } from "@zula/api"
import { useListProviders } from "@zula/api/endpoints"
import type { OAuthProviderInfo, OAuthSignInResult } from "@zula/oauth"
import { getProviderDefinition } from "@zula/oauth"
import { View } from "react-native"
import { Button } from "@/components/ui/button"
import { Text } from "@/components/ui/text"
import { useNativeOAuthSignIn } from "@/lib/oauth/use-native-oauth-sign-in"
import { readStoredSession, writeStoredSession } from "@/lib/session-storage"

type OAuthSignInButtonsProps = {
  disabled?: boolean
  onSuccess: () => Promise<void> | void
}

type ProviderSignInButtonProps = {
  provider: OAuthProviderInfo
  disabled?: boolean
  onAuthenticated: () => Promise<void> | void
}

function ProviderSignInButton({ provider, disabled, onAuthenticated }: ProviderSignInButtonProps) {
  const definition = getProviderDefinition(provider.id)

  const oauth = useNativeOAuthSignIn({
    provider,
    onSuccess: async (result: OAuthSignInResult) => {
      const stored = await readStoredSession()
      const next = await authenticateWithIdToken({
        provider: result.provider,
        idToken: result.idToken,
        providerRefreshToken: result.refreshToken,
        sessionId: stored?.sessionId,
        deviceInfo: "native",
      })
      await writeStoredSession(next)
      await onAuthenticated()
    },
    onError: (error) => {
      console.error(`${provider.id} sign-in failed`, error)
    },
  })

  if (!oauth.ready) return null

  return (
    <Button
      variant="outline"
      className="w-full"
      disabled={disabled || oauth.pending}
      onPress={() => void oauth.signIn()}
    >
      <Text>{oauth.pending ? "Signing in…" : `Continue with ${definition?.label ?? provider.id}`}</Text>
    </Button>
  )
}

export function OAuthSignInButtons({ disabled, onSuccess }: OAuthSignInButtonsProps) {
  const providersQuery = useListProviders()
  const providers = providersQuery.data?.data.providers ?? []

  if (providersQuery.isLoading) {
    return (
      <Button variant="outline" disabled>
        <Text>Loading sign-in options…</Text>
      </Button>
    )
  }

  if (providers.length === 0) {
    return null
  }

  return (
    <View className="flex-col gap-2">
      {providers.map((provider) => (
        <ProviderSignInButton
          key={provider.id}
          provider={provider}
          disabled={disabled}
          onAuthenticated={onSuccess}
        />
      ))}
    </View>
  )
}
