import { GoogleLogin, GoogleOAuthProvider } from "@react-oauth/google"
import { authenticateWithIdToken } from "@zula/api"
import { useListProviders } from "@zula/api/endpoints"
import { Button } from "@/components/ui/button"

type GoogleSignInButtonProps = {
  disabled?: boolean
  onSuccess: () => Promise<void> | void
}

export function GoogleSignInButton({ disabled, onSuccess }: GoogleSignInButtonProps) {
  const providersQuery = useListProviders()
  const google = providersQuery.data?.data.providers.find((provider) => provider.id === "google")

  if (providersQuery.isLoading) {
    return (
      <Button variant="outline" type="button" disabled>
        Loading Google…
      </Button>
    )
  }

  if (!google?.clientId) {
    return null
  }

  return (
    <GoogleOAuthProvider clientId={google.clientId}>
      <div className="[&>div]:w-full [&_iframe]:!w-full">
        <GoogleLogin
          width="100%"
          text="continue_with"
          shape="rectangular"
          theme="outline"
          onSuccess={async (response) => {
            if (!response.credential) return
            await authenticateWithIdToken({
              provider: "google",
              idToken: response.credential,
            })
            await onSuccess()
          }}
          onError={() => {
            console.error("Google sign-in failed")
          }}
        />
      </div>
    </GoogleOAuthProvider>
  )
}
