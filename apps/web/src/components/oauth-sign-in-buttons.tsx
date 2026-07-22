import { authenticateWithIdToken } from "@zula/api"
import { useListProviders } from "@zula/api/endpoints"
import { getProviderDefinition } from "@zula/oauth"
import { OAuthSignInButton } from "@zula/oauth/web"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

type OAuthSignInButtonsProps = {
  disabled?: boolean
  onSuccess: () => Promise<void> | void
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
      {providers.map((provider) => {
        const definition = getProviderDefinition(provider.id)
        const usesEmbeddedButton = definition?.web?.strategy === "google-identity"

        return (
          <OAuthSignInButton
            key={provider.id}
            provider={provider}
            disabled={disabled}
            label={`Continue with ${definition?.label ?? provider.id}`}
            className={cn(
              usesEmbeddedButton
                ? "[&>div]:w-full [&_iframe]:!w-full"
                : "inline-flex h-9 w-full items-center justify-center rounded-md border border-input bg-background px-4 py-2 text-sm font-medium shadow-xs transition-colors hover:bg-accent hover:text-accent-foreground disabled:pointer-events-none disabled:opacity-50"
            )}
            onSuccess={async (result) => {
              await authenticateWithIdToken({
                provider: result.provider,
                idToken: result.idToken,
                providerRefreshToken: result.refreshToken,
              })
              await onSuccess()
            }}
            onError={(error) => {
              console.error(`${provider.id} sign-in failed`, error)
            }}
          />
        )
      })}
    </div>
  )
}
