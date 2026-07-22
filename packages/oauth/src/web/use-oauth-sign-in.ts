import { useCallback, useEffect, useRef, useState } from "react"
import { getProviderDefinition } from "../providers/registry"
import type { OAuthProviderInfo, OAuthSignInResult } from "../types"
import { renderGoogleIdentityButton } from "./google-identity"
import { signInOnWeb } from "./sign-in"

export type UseOAuthSignInOptions = {
  provider?: OAuthProviderInfo
  redirectPath?: string
  onSuccess?: (result: OAuthSignInResult) => void | Promise<void>
  onError?: (error: Error) => void
}

export function useOAuthSignIn({
  provider,
  redirectPath,
  onSuccess,
  onError,
}: UseOAuthSignInOptions) {
  const [pending, setPending] = useState(false)
  const buttonContainerRef = useRef<HTMLDivElement | null>(null)
  const definition = provider ? getProviderDefinition(provider.id) : undefined
  const usesGoogleButton = definition?.web?.strategy === "google-identity"

  const signIn = useCallback(async () => {
    if (!provider) {
      onError?.(new Error("OAuth provider not configured"))
      return
    }

    setPending(true)
    try {
      const result = await signInOnWeb({
        provider,
        redirectPath,
        googleButtonContainer: usesGoogleButton ? buttonContainerRef.current ?? undefined : undefined,
      })
      await onSuccess?.(result)
    } catch (error) {
      onError?.(error instanceof Error ? error : new Error("OAuth sign-in failed"))
    } finally {
      setPending(false)
    }
  }, [provider, redirectPath, usesGoogleButton, onSuccess, onError])

  useEffect(() => {
    if (!provider || !usesGoogleButton || !buttonContainerRef.current) return

    let disposed = false
    let cleanup: (() => void) | undefined

    void renderGoogleIdentityButton({
      clientId: provider.clientId,
      container: buttonContainerRef.current,
      width: "100%",
      onSuccess: async (result) => {
        setPending(true)
        try {
          await onSuccess?.(result)
        } catch (error) {
          onError?.(error instanceof Error ? error : new Error("OAuth sign-in failed"))
        } finally {
          setPending(false)
        }
      },
      onError: (error) => {
        onError?.(error)
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
  }, [provider, usesGoogleButton, onSuccess, onError])

  return {
    signIn,
    pending,
    ready: Boolean(provider?.clientId),
    usesEmbeddedButton: usesGoogleButton,
    buttonContainerRef,
  }
}
