import {
  buildAuthorizeUrl,
  getProviderDefinition,
  type OAuthProviderInfo,
  type OAuthSignInResult,
  parseOAuthRedirect,
  randomString,
} from "@zula/oauth"

const OAUTH_MESSAGE_TYPE = "zula.oauth.callback"

export type PopupSignInOptions = {
  provider: OAuthProviderInfo
  redirectPath?: string
  popupName?: string
  popupFeatures?: string
  timeoutMs?: number
}

/** Prefer redirect paths already registered in Google Cloud Console. */
function defaultRedirectPath(_: string): string {
  return "/oauth/callback"
}

export async function signInWithPopup({
  provider,
  redirectPath = defaultRedirectPath(provider.id),
  popupName = "zula-oauth",
  popupFeatures = "popup,width=500,height=700",
  timeoutMs = 120_000,
}: PopupSignInOptions): Promise<OAuthSignInResult> {
  const definition = getProviderDefinition(provider.id)
  const state = randomString()
  const nonce = randomString()
  const redirectUri = `${window.location.origin}${redirectPath}`
  const authorizeUrl = buildAuthorizeUrl({
    provider,
    redirectUri,
    state,
    nonce,
    extraParams: definition?.extraAuthParams,
  })

  if (import.meta.env.DEV) {
    console.info(`[zula] OAuth authorize ${provider.id}`, {
      clientId: provider.clientId,
      redirectUri,
    })
  }

  return new Promise<OAuthSignInResult>((resolve, reject) => {
    const popup = window.open(authorizeUrl, popupName, popupFeatures)
    if (!popup) {
      reject(new Error("Popup blocked"))
      return
    }

    const timeout = window.setTimeout(() => {
      cleanup()
      reject(new Error("OAuth sign-in timed out"))
    }, timeoutMs)

    function onMessage(event: MessageEvent) {
      if (event.origin !== window.location.origin) return
      if (!event.data || event.data.type !== OAUTH_MESSAGE_TYPE) return

      cleanup()

      if (event.data.error) {
        reject(new Error(event.data.error))
        return
      }

      if (event.data.state !== state) {
        reject(new Error("OAuth state mismatch"))
        return
      }

      if (!event.data.idToken) {
        reject(new Error("OAuth response missing id_token"))
        return
      }

      resolve({
        provider: provider.id,
        idToken: event.data.idToken,
        accessToken: event.data.accessToken ?? null,
        refreshToken: event.data.refreshToken ?? null,
      })
    }

    function cleanup() {
      window.clearTimeout(timeout)
      window.removeEventListener("message", onMessage)
      if (!popup?.closed) popup?.close()
    }

    window.addEventListener("message", onMessage)
  })
}

export function handleOAuthCallbackPage(): void {
  const parsed = parseOAuthRedirect(window.location.href)

  if (window.opener) {
    window.opener.postMessage(
      {
        type: OAUTH_MESSAGE_TYPE,
        idToken: parsed.idToken,
        accessToken: parsed.accessToken,
        refreshToken: parsed.refreshToken,
        state: parsed.state,
        error: parsed.error ?? parsed.errorDescription,
      },
      window.location.origin
    )
    window.close()
  }
}
