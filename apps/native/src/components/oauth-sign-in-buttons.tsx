import { useListProviders } from "@zula/api/endpoints"
import type { OAuthProviderInfo } from "@zula/api/model"
import type { OAuthSignInResult } from "@zula/oauth"
import { getProviderDefinition } from "@zula/oauth"
import { useState } from "react"
import { View } from "react-native"
import { Button } from "@/components/ui/button"
import { Text } from "@/components/ui/text"
import { authenticateNativeCode, authenticateNativeDevBypass } from "@/lib/api"
import { useNativeOAuthSignIn } from "@/lib/oauth/use-native-oauth-sign-in"

type OAuthSignInButtonsProps = {
  disabled?: boolean
  onSuccess: () => Promise<void> | void
}

type ProviderSignInButtonProps = {
  provider: OAuthProviderInfo
  disabled?: boolean
  onAuthenticated: () => Promise<void> | void
}

function isDevProvider(provider: OAuthProviderInfo) {
  return provider.id === "dev"
}

function ProviderSignInButton({ provider, disabled, onAuthenticated }: ProviderSignInButtonProps) {
  const definition = getProviderDefinition(provider.id)

  const oauth = useNativeOAuthSignIn({
    provider,
    onSuccess: async (result: OAuthSignInResult) => {
      if (!result.code || !result.redirectUri) {
        throw new Error("OAuth response missing authorization code")
      }
      await authenticateNativeCode({
        provider: result.provider,
        code: result.code,
        codeVerifier: result.codeVerifier,
        redirectUri: result.redirectUri,
      })
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
      <Text>
        {oauth.pending ? "Signing in…" : `Continue with ${definition?.label ?? provider.id}`}
      </Text>
    </Button>
  )
}

function DevSignInButton({ provider, disabled, onAuthenticated }: ProviderSignInButtonProps) {
  const definition = getProviderDefinition("dev")
  const email = provider.scopes[0]
  const [pending, setPending] = useState(false)

  return (
    <Button
      variant="secondary"
      className="w-full"
      disabled={disabled || pending || !provider.clientId}
      onPress={() => {
        setPending(true)
        void authenticateNativeDevBypass(provider.clientId)
          .then(() => onAuthenticated())
          .catch((error: unknown) => {
            console.error("Dev sign-in failed", error)
          })
          .finally(() => {
            setPending(false)
          })
      }}
    >
      <Text>
        {pending
          ? "Signing in…"
          : email
            ? `Dev sign in (${email})`
            : `Continue with ${definition?.label ?? "Dev"}`}
      </Text>
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
      {providers.map((provider) =>
        isDevProvider(provider) ? (
          <DevSignInButton
            key={provider.id}
            provider={provider}
            disabled={disabled}
            onAuthenticated={onSuccess}
          />
        ) : (
          <ProviderSignInButton
            key={provider.id}
            provider={provider}
            disabled={disabled}
            onAuthenticated={onSuccess}
          />
        )
      )}
    </View>
  )
}
