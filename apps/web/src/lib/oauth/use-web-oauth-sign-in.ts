import { useCallback } from "react"
import type { OAuthProviderInfo, OAuthSignInResult } from "@zula/oauth"
import { useOAuthSignIn } from "@zula/oauth/react"
import { signInOnWeb } from "./sign-in"

export type UseWebOAuthSignInOptions = {
  provider?: OAuthProviderInfo
  onSuccess?: (result: OAuthSignInResult) => void | Promise<void>
  onError?: (error: Error) => void
}

export function useWebOAuthSignIn({ provider, onSuccess, onError }: UseWebOAuthSignInOptions) {
  const signInExecutor = useCallback(
    async (activeProvider: OAuthProviderInfo) => signInOnWeb(activeProvider),
    []
  )

  return useOAuthSignIn({
    provider,
    signIn: signInExecutor,
    onSuccess,
    onError,
  })
}
