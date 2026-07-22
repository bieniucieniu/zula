import { getProviderDefinition } from "../providers/registry"
import type { OAuthProviderInfo, OAuthSignInResult } from "../types"
import { renderGoogleIdentityButton, signInWithGoogleIdentity } from "./google-identity"
import { signInWithPopup } from "./popup-sign-in"

export type WebSignInOptions = {
  provider: OAuthProviderInfo
  redirectPath?: string
  googleButtonContainer?: HTMLElement
  googleButtonWidth?: number | string
}

export async function signInOnWeb({
  provider,
  redirectPath,
  googleButtonContainer,
  googleButtonWidth,
}: WebSignInOptions): Promise<OAuthSignInResult> {
  const definition = getProviderDefinition(provider.id)
  const strategy = definition?.web?.strategy ?? "popup"

  if (strategy === "google-identity") {
    if (googleButtonContainer) {
      return new Promise<OAuthSignInResult>((resolve, reject) => {
        void renderGoogleIdentityButton({
          clientId: provider.clientId,
          container: googleButtonContainer,
          width: googleButtonWidth,
          onSuccess: resolve,
          onError: reject,
        })
      })
    }

    return signInWithGoogleIdentity(provider.clientId)
  }

  return signInWithPopup({ provider, redirectPath })
}

export { handleOAuthCallbackPage, getOAuthCallbackRedirectPath } from "./popup-sign-in"
export { renderGoogleIdentityButton } from "./google-identity"
