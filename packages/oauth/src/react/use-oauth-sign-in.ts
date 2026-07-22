import { useMutation } from "@tanstack/react-query"
import { useCallback, useRef } from "react"
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
  const onSuccessRef = useRef(onSuccess)
  const onErrorRef = useRef(onError)

  onSuccessRef.current = onSuccess
  onErrorRef.current = onError

  const mutation = useMutation({
    mutationKey: ["oauth", "sign-in", provider?.id],
    mutationFn: async (activeProvider: OAuthProviderInfo) => signIn(activeProvider),
    onSuccess: async (result) => {
      await onSuccessRef.current?.(result)
    },
    onError: (error) => {
      onErrorRef.current?.(
        error instanceof Error ? error : new Error("OAuth sign-in failed")
      )
    },
  })

  const execute = useCallback(async () => {
    if (!enabled || !provider) {
      onErrorRef.current?.(new Error("OAuth provider not configured"))
      return
    }

    await mutation.mutateAsync(provider)
  }, [enabled, provider, mutation])

  return {
    signIn: execute,
    pending: mutation.isPending,
    ready: enabled && Boolean(provider?.clientId),
    mutation,
  }
}
