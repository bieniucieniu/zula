import { useCallback, useLayoutEffect, useRef, useState } from "react"
import type { OAuthProviderInfo, OAuthSignInResult } from "@zula/oauth"
import { useOAuthSignIn } from "@zula/oauth/react"
import { formatGoogleSignInError, getGoogleSignInOriginHint } from "./google-errors"
import { renderGoogleIdentityButton, signInOnWeb } from "./sign-in"

export type UseWebOAuthSignInOptions = {
  provider?: OAuthProviderInfo
  onSuccess?: (result: OAuthSignInResult) => void | Promise<void>
  onError?: (error: Error) => void
}

export function useWebOAuthSignIn({ provider, onSuccess, onError }: UseWebOAuthSignInOptions) {
  const buttonContainerRef = useRef<HTMLDivElement | null>(null)
  const onSuccessRef = useRef(onSuccess)
  const onErrorRef = useRef(onError)
  const usesGoogleButton = provider?.id === "google"
  const [googlePending, setGooglePending] = useState(false)
  const [googleError, setGoogleError] = useState<string | null>(null)

  onSuccessRef.current = onSuccess
  onErrorRef.current = onError

  const signInExecutor = useCallback(
    async (activeProvider: OAuthProviderInfo) => signInOnWeb(activeProvider),
    []
  )

  const oauth = useOAuthSignIn({
    provider: usesGoogleButton ? undefined : provider,
    signIn: signInExecutor,
    onSuccess,
    onError,
  })

  useLayoutEffect(() => {
    if (!provider || !usesGoogleButton) return

    const container = buttonContainerRef.current
    if (!container) return

    let disposed = false
    let cleanup: (() => void) | undefined

    void renderGoogleIdentityButton({
      clientId: provider.clientId,
      container,
      onSuccess: async (result) => {
        setGoogleError(null)
        setGooglePending(true)
        try {
          await onSuccessRef.current?.(result)
        } catch (error) {
          onErrorRef.current?.(
            error instanceof Error ? error : new Error("OAuth sign-in failed")
          )
        } finally {
          setGooglePending(false)
        }
      },
      onError: (error) => {
        setGoogleError(formatGoogleSignInError(error, provider.clientId))
        onErrorRef.current?.(error)
      },
    }).then((dispose) => {
      if (disposed) {
        dispose()
        return
      }
      cleanup = dispose
    })

    return () => {
      disposed = true
      cleanup?.()
    }
  }, [provider?.id, provider?.clientId, usesGoogleButton])

  return {
    signIn: oauth.signIn,
    pending: usesGoogleButton ? googlePending : oauth.pending,
    ready: Boolean(provider?.clientId),
    usesGoogleButton,
    buttonContainerRef,
    error: googleError,
    originHint: usesGoogleButton ? getGoogleSignInOriginHint(provider?.clientId) : null,
  }
}
