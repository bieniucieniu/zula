export function getGoogleSignInOriginHint(clientId?: string) {
  const origin = typeof window !== "undefined" ? window.location.origin : "your-app-origin"
  const id = clientId?.trim() || "web client"
  const localhostHint =
    origin.includes("127.0.0.1") ? " Also add http://localhost:3000 and bare http://localhost." : ""
  return `Add ${origin} to Google Cloud Console → Credentials → OAuth Web client (${id}) → Authorized JavaScript origins.${localhostHint} Click Save, wait a few minutes, and confirm GOOGLE_CLIENT_ID matches that client.`
}

export function formatGoogleSignInError(error: unknown, clientId?: string) {
  const message = error instanceof Error ? error.message : String(error)
  if (message.toLowerCase().includes("origin")) {
    return getGoogleSignInOriginHint(clientId)
  }
  return message
}
