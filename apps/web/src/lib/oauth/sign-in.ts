import type { OAuthProviderInfo, OAuthSignInResult } from "@zula/oauth"
import { signInWithPopup } from "./popup-sign-in"

export async function signInOnWeb(provider: OAuthProviderInfo): Promise<OAuthSignInResult> {
  return signInWithPopup({ provider })
}

export { handleOAuthCallbackPage } from "./popup-sign-in"
