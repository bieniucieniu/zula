const OAUTH_MESSAGE_TYPE = "zula.oauth.complete"

export function signInWithBackendGooglePopup(timeoutMs = 120_000): Promise<void> {
  return new Promise((resolve, reject) => {
    const popup = window.open(
      "/api/auth/oauth/google/start?mode=popup",
      "zula-oauth",
      "popup,width=500,height=700"
    )
    if (!popup) {
      reject(new Error("Popup blocked"))
      return
    }

    function cleanup() {
      window.clearTimeout(timeout)
      window.removeEventListener("message", onMessage)
      if (!popup.closed) popup.close()
    }

    const timeout = window.setTimeout(() => {
      cleanup()
      reject(new Error("OAuth sign-in timed out"))
    }, timeoutMs)

    function onMessage(event: MessageEvent) {
      if (event.origin !== window.location.origin) return
      if (!event.data || event.data.type !== OAUTH_MESSAGE_TYPE) return

      cleanup()

      if (event.data.success) {
        resolve()
        return
      }

      reject(new Error(event.data.error ?? "OAuth sign-in failed"))
    }

    window.addEventListener("message", onMessage)
  })
}
