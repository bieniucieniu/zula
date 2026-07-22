import { useCallback, useEffect, useRef, useState } from "react"
import type { OAuthProviderInfo, OAuthSignInResult } from "@zula/oauth"
import { useOAuthSignIn } from "@zula/oauth/react"
import { renderGoogleIdentityButton, signInOnWeb } from "./sign-in"

export type UseWebOAuthSignInOptions = {
  provider?: OAuthProviderInfo
  onSuccess?: (result: OAuthSignInResult) => void | Promise<void>
  onError?: (error: Error) => void
}

export function useWebOAuthSignIn({ provider, onSuccess, onError }: UseWebOAuthSignInOptions) {
  const buttonContainerRef = useRef<HTMLDivElement | null>(null)
  const usesGoogleButton = provider?.id === "google"
  const [googlePending, setGooglePending] = useState(false)

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

  useEffect(() => {
    if (!provider || !usesGoogleButton || !buttonContainerRef.current) return

    let disposed = false
    let cleanup: (() => void) | undefined

    void renderGoogleIdentityButton({
      clientId: provider.clientId,
      container: buttonContainerRef.current,
      width: "100%",
      onSuccess: async (result) => {
        setGooglePending(true)
        try {
          await onSuccess?.(result)
        } catch (error) {
          onError?.(error instanceof Error ? error : new Error("OAuth sign-in failed"))
        } finally {
          setGooglePending(false)
        }
      },
      onError,
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
  }, [provider, usesGoogleButton, onSuccess, onError])

  return {
    signIn: oauth.signIn,
    pending: usesGoogleButton ? googlePending : oauth.pending,
    ready: Boolean(provider?.clientId),
    usesGoogleButton,
    buttonContainerRef,
  }
}
