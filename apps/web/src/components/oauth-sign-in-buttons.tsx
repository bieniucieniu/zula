import { authenticateWithDevBypass } from "@zula/api"
import { useListProviders } from "@zula/api/endpoints"
import type { OAuthProviderInfo } from "@zula/api/model"
import { getProviderDefinition } from "@zula/oauth"
import { Button } from "@/components/ui/button"
import { signInWithBackendGooglePopup } from "@/lib/oauth/backend-popup-sign-in"
import { useState } from "react"

type OAuthSignInButtonsProps = {
  disabled?: boolean
  onSuccess: () => Promise<void> | void
}

function isDevProvider(provider: OAuthProviderInfo) {
  return provider.id === "dev"
}

function GoogleSignInButton({
  disabled,
  onAuthenticated,
}: {
  disabled?: boolean
  onAuthenticated: () => Promise<void> | void
}) {
  const definition = getProviderDefinition("google")
  const [pending, setPending] = useState(false)

  return (
    <Button
      type="button"
      variant="outline"
      className="w-full"
      disabled={disabled || pending}
      onClick={() => {
        setPending(true)
        void signInWithBackendGooglePopup()
          .then(() => onAuthenticated())
          .catch((error: unknown) => {
            console.error("google sign-in failed", error)
          })
          .finally(() => {
            setPending(false)
          })
      }}
    >
      {pending ? "Signing in…" : `Continue with ${definition?.label ?? "Google"}`}
    </Button>
  )
}

function DevSignInButton({
  provider,
  disabled,
  onAuthenticated,
}: {
  provider: OAuthProviderInfo
  disabled?: boolean
  onAuthenticated: () => Promise<void> | void
}) {
  const definition = getProviderDefinition("dev")
  const email = provider.scopes[0]
  const [pending, setPending] = useState(false)

  return (
    <Button
      type="button"
      variant="secondary"
      className="w-full"
      disabled={disabled || pending || !provider.clientId}
      onClick={() => {
        setPending(true)
        void authenticateWithDevBypass({ secret: provider.clientId })
          .then(() => onAuthenticated())
          .catch((error: unknown) => {
            console.error("dev sign-in failed", error)
          })
          .finally(() => {
            setPending(false)
          })
      }}
    >
      {pending
        ? "Signing in…"
        : email
          ? `Dev sign in (${email})`
          : `Continue with ${definition?.label ?? "Dev"}`}
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
      {providers.map((provider) => {
        if (isDevProvider(provider)) {
          return (
            <DevSignInButton
              key={provider.id}
              provider={provider}
              disabled={disabled}
              onAuthenticated={onSuccess}
            />
          )
        }
        if (provider.id === "google") {
          return (
            <GoogleSignInButton
              key={provider.id}
              disabled={disabled}
              onAuthenticated={onSuccess}
            />
          )
        }
        return null
      })}
    </div>
  )
}
