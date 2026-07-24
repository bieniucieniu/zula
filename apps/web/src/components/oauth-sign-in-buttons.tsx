import { useListProviders } from "@zula/api/endpoints"
import { getProviderDefinition } from "@zula/oauth"
import { Button } from "@/components/ui/button"
import { signInWithBackendGooglePopup } from "@/lib/oauth/backend-popup-sign-in"
import { useState } from "react"

type OAuthSignInButtonsProps = {
  disabled?: boolean
  onSuccess: () => Promise<void> | void
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

  const google = providers.find((provider) => provider.id === "google")
  if (!google) {
    return null
  }

  return (
    <div className="flex flex-col gap-2">
      <GoogleSignInButton disabled={disabled} onAuthenticated={onSuccess} />
    </div>
  )
}
