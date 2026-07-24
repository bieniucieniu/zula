import type { OAuthProviderInfo, OAuthSignInResult } from "@zula/oauth"
import { useOAuthSignIn } from "@zula/oauth/react"
import { signInWithPopup } from "./popup-sign-in"

export type UseWebOAuthSignInOptions = {
  provider?: OAuthProviderInfo
  onSuccess?: (result: OAuthSignInResult) => void | Promise<void>
  onError?: (error: Error) => void
}

export function useWebOAuthSignIn({ provider, onSuccess, onError }: UseWebOAuthSignInOptions) {
  return useOAuthSignIn({
    provider,
    signIn: signInWithPopup,
    onSuccess,
    onError,
  })
}
