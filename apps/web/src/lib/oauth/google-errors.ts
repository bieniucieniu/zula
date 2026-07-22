export function getGoogleSignInOriginHint(clientId?: string) {
  const origin = typeof window !== "undefined" ? window.location.origin : "your-app-origin"
  return `Add ${origin} to Google Cloud Console → APIs & Services → Credentials → OAuth 2.0 Client (${clientId ?? "web client"}) → Authorized JavaScript origins. Use a Web application client ID, not Android/iOS.`
}

export function formatGoogleSignInError(error: unknown, clientId?: string) {
  const message = error instanceof Error ? error.message : String(error)
  if (message.toLowerCase().includes("origin")) {
    return getGoogleSignInOriginHint(clientId)
  }
  return message
}
