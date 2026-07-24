import { authenticateWithIdToken } from "@zula/api"
import { useListProviders } from "@zula/api/endpoints"
import type { OAuthProviderInfo, OAuthSignInResult } from "@zula/oauth"
import { getProviderDefinition } from "@zula/oauth"
import { Button } from "@/components/ui/button"
import { useWebOAuthSignIn } from "@/lib/oauth/use-web-oauth-sign-in"

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

  const oauth = useWebOAuthSignIn({
    provider,
    onSuccess: async (result: OAuthSignInResult) => {
      await authenticateWithIdToken({
        provider: result.provider,
        idToken: result.idToken,
        providerRefreshToken: result.refreshToken,
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
      type="button"
      variant="outline"
      className="w-full"
      disabled={disabled || oauth.pending}
      onClick={() => void oauth.signIn()}
    >
      {oauth.pending ? "Signing in…" : `Continue with ${definition?.label ?? provider.id}`}
    </Button>
  )
}

export function OAuthSignInButtons({ disabled, onSuccess }: OAuthSignInButtonsProps) {
  const providersQuery = useListProviders()
  const providers = providersQuery.data?.data.providers ?? []

  if (providersQuery.isLoading) {
    return (
      <Button variant="outline" type="button" disabled>
        Loading sign-in options…
      </Button>
    )
  }

  if (providers.length === 0) {
    return null
  }

  return (
    <div className="flex flex-col gap-2">
      {providers.map((provider) => (
        <ProviderSignInButton
          key={provider.id}
          provider={provider}
          disabled={disabled}
          onAuthenticated={onSuccess}
        />
      ))}
    </div>
  )
}
