import type { OAuthProviderInfo, OAuthSignInResult } from "@zula/oauth"
import { renderGoogleIdentityButton } from "./google-identity"
import { signInWithPopup } from "./popup-sign-in"

export async function signInOnWeb(provider: OAuthProviderInfo): Promise<OAuthSignInResult> {
  if (provider.id === "google") {
    throw new Error("Google web sign-in requires a button container")
  }

  return signInWithPopup({ provider })
}

export async function signInWithGoogleButton(
  provider: OAuthProviderInfo,
  container: HTMLElement
): Promise<OAuthSignInResult> {
  return new Promise<OAuthSignInResult>((resolve, reject) => {
    void renderGoogleIdentityButton({
      clientId: provider.clientId,
      container,
      width: "100%",
      onSuccess: resolve,
      onError: reject,
    })
  })
}

export { handleOAuthCallbackPage } from "./popup-sign-in"
export { renderGoogleIdentityButton } from "./google-identity"
