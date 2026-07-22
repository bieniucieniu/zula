import { useCallback, useState } from "react"
import type { OAuthProviderInfo, OAuthSignInExecutor, OAuthSignInResult } from "../types"

export type UseOAuthSignInOptions = {
  provider?: OAuthProviderInfo
  signIn: OAuthSignInExecutor
  enabled?: boolean
  onSuccess?: (result: OAuthSignInResult) => void | Promise<void>
  onError?: (error: Error) => void
}

export function useOAuthSignIn({
  provider,
  signIn,
  enabled = true,
  onSuccess,
  onError,
}: UseOAuthSignInOptions) {
  const [pending, setPending] = useState(false)

  const execute = useCallback(async () => {
    if (!enabled || !provider) {
      onError?.(new Error("OAuth provider not configured"))
      return
    }

    setPending(true)
    try {
      const result = await signIn(provider)
      await onSuccess?.(result)
    } catch (error) {
      onError?.(error instanceof Error ? error : new Error("OAuth sign-in failed"))
    } finally {
      setPending(false)
    }
  }, [enabled, provider, signIn, onSuccess, onError])

  return {
    signIn: execute,
    pending,
    ready: enabled && Boolean(provider?.clientId),
  }
}
