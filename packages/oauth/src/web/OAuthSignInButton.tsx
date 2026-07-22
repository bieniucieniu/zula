import { useOAuthSignIn } from "./use-oauth-sign-in"
import type { OAuthProviderInfo, OAuthSignInResult } from "../types"

export type OAuthSignInButtonProps = {
  provider: OAuthProviderInfo
  label?: string
  disabled?: boolean
  className?: string
  redirectPath?: string
  onSuccess: (result: OAuthSignInResult) => void | Promise<void>
  onError?: (error: Error) => void
}

export function OAuthSignInButton({
  provider,
  label,
  disabled,
  className,
  redirectPath,
  onSuccess,
  onError,
}: OAuthSignInButtonProps) {
  const oauth = useOAuthSignIn({
    provider,
    redirectPath,
    onSuccess,
    onError,
  })

  if (!oauth.ready) return null

  if (oauth.usesEmbeddedButton) {
    return (
      <div
        ref={oauth.buttonContainerRef}
        className={className}
        data-provider={provider.id}
        aria-disabled={disabled || oauth.pending}
        style={
          disabled || oauth.pending
            ? { pointerEvents: "none", opacity: 0.5 }
            : undefined
        }
      />
    )
  }

  return (
    <button
      type="button"
      className={className}
      disabled={disabled || oauth.pending}
      onClick={() => void oauth.signIn()}
    >
      {oauth.pending ? "Signing in…" : (label ?? `Continue with ${provider.id}`)}
    </button>
  )
}
