import type { OAuthSignInResult } from "@zula/oauth"
import { loadScript } from "./load-script"

type GoogleCredentialResponse = {
  credential?: string
}

type GoogleIdApi = {
  initialize: (config: {
    client_id: string
    callback: (response: GoogleCredentialResponse) => void
    error_callback?: (error: { type?: string; message?: string }) => void
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
      width?: number
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
const GOOGLE_SCRIPT_REFERRER_POLICY: ReferrerPolicy = import.meta.env.DEV
  ? "no-referrer-when-downgrade"
  : "strict-origin-when-cross-origin"
const MIN_BUTTON_WIDTH = 40
const MAX_BUTTON_WIDTH = 400
const DEFAULT_BUTTON_WIDTH = 320

let googleInitPromise: Promise<GoogleIdApi> | null = null
let initializedClientId: string | null = null
let credentialHandler: ((response: GoogleCredentialResponse) => void) | null = null

async function getGoogleIdentityApi(): Promise<GoogleIdApi> {
  if (!googleInitPromise) {
    googleInitPromise = loadScript(GOOGLE_SCRIPT_URL, {
      referrerPolicy: GOOGLE_SCRIPT_REFERRER_POLICY,
    }).then(() => {
      const api = window.google?.accounts?.id
      if (!api) throw new Error("Google Identity Services unavailable")
      return api
    })
  }

  return googleInitPromise
}

function resolveButtonWidth(container: HTMLElement): number {
  const measured = Math.floor(container.getBoundingClientRect().width)
  if (measured <= 0) return DEFAULT_BUTTON_WIDTH
  return Math.min(MAX_BUTTON_WIDTH, Math.max(MIN_BUTTON_WIDTH, measured))
}

type GoogleInitOptions = {
  onError?: (error: Error) => void
}

async function ensureGoogleInitialized(
  clientId: string,
  api: GoogleIdApi,
  { onError }: GoogleInitOptions = {}
) {
  if (initializedClientId === clientId) return

  api.initialize({
    client_id: clientId,
    callback: (response) => {
      credentialHandler?.(response)
    },
    error_callback: (error) => {
      const message = error.message?.trim() || error.type || "Google sign-in failed"
      onError?.(new Error(message))
    },
    auto_select: false,
    cancel_on_tap_outside: true,
  })

  initializedClientId = clientId
}

export type GoogleIdentityButtonOptions = {
  clientId: string
  container: HTMLElement
  onSuccess: (result: OAuthSignInResult) => void
  onError?: (error: Error) => void
}

export async function renderGoogleIdentityButton({
  clientId,
  container,
  onSuccess,
  onError,
}: GoogleIdentityButtonOptions): Promise<() => void> {
  const normalizedClientId = clientId.trim()
  const api = await getGoogleIdentityApi()

  credentialHandler = (response) => {
    if (!response.credential) {
      onError?.(new Error("Google sign-in returned no credential"))
      return
    }

    onSuccess({
      provider: "google",
      idToken: response.credential,
    })
  }

  await ensureGoogleInitialized(normalizedClientId, api, { onError })

  const render = () => {
    container.replaceChildren()
    api.renderButton(container, {
      type: "standard",
      theme: "outline",
      size: "large",
      text: "continue_with",
      shape: "rectangular",
      width: resolveButtonWidth(container),
    })
  }

  render()

  if (import.meta.env.DEV) {
    console.info(
      `[zula] Google GIS clientId=${normalizedClientId} origin=${window.location.origin}`
    )
  }

  const observer = new ResizeObserver(() => {
    render()
  })
  observer.observe(container)

  return () => {
    observer.disconnect()
    container.replaceChildren()
    if (credentialHandler) {
      credentialHandler = null
    }
  }
}
