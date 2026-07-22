import type { OAuthSignInResult } from "@zula/oauth"
import { loadScript } from "./load-script"

type GoogleCredentialResponse = {
  credential?: string
}

type GoogleIdApi = {
  initialize: (config: {
    client_id: string
    callback: (response: GoogleCredentialResponse) => void
    auto_select?: boolean
    cancel_on_tap_outside?: boolean
  }) => void
  renderButton: (
    parent: HTMLElement,
    options: {
      type?: "standard" | "icon"
      theme?: "outline" | "filled_blue" | "filled_black"
      size?: "large" | "medium" | "small"
      text?: "signin_with" | "signup_with" | "continue_with" | "signin"
      shape?: "rectangular" | "pill" | "circle" | "square"
      width?: number | string
    }
  ) => void
}

declare global {
  interface Window {
    google?: {
      accounts?: {
        id?: GoogleIdApi
      }
    }
  }
}

const GOOGLE_SCRIPT_URL = "https://accounts.google.com/gsi/client"

let googleInitPromise: Promise<GoogleIdApi> | null = null

async function getGoogleIdentityApi(): Promise<GoogleIdApi> {
  if (!googleInitPromise) {
    googleInitPromise = loadScript(GOOGLE_SCRIPT_URL).then(() => {
      const api = window.google?.accounts?.id
      if (!api) throw new Error("Google Identity Services unavailable")
      return api
    })
  }

  return googleInitPromise
}

export type GoogleIdentityButtonOptions = {
  clientId: string
  container: HTMLElement
  width?: number | string
  onSuccess: (result: OAuthSignInResult) => void
  onError?: (error: Error) => void
}

export async function renderGoogleIdentityButton({
  clientId,
  container,
  width = "100%",
  onSuccess,
  onError,
}: GoogleIdentityButtonOptions): Promise<() => void> {
  const api = await getGoogleIdentityApi()

  api.initialize({
    client_id: clientId,
    callback: (response) => {
      if (!response.credential) {
        onError?.(new Error("Google sign-in returned no credential"))
        return
      }

      onSuccess({
        provider: "google",
        idToken: response.credential,
      })
    },
    auto_select: false,
    cancel_on_tap_outside: true,
  })

  container.replaceChildren()
  api.renderButton(container, {
    type: "standard",
    theme: "outline",
    size: "large",
    text: "continue_with",
    shape: "rectangular",
    width,
  })

  return () => {
    container.replaceChildren()
  }
}
