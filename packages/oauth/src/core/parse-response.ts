export function parseOAuthRedirect(url: string): {
  idToken?: string
  accessToken?: string
  refreshToken?: string
  state?: string
  error?: string
  errorDescription?: string
} {
  const target = new URL(url)
  const params = new URLSearchParams(
    target.hash.startsWith("#") ? target.hash.slice(1) : target.search.slice(1)
  )

  return {
    idToken: params.get("id_token") ?? undefined,
    accessToken: params.get("access_token") ?? undefined,
    refreshToken: params.get("refresh_token") ?? undefined,
    state: params.get("state") ?? undefined,
    error: params.get("error") ?? undefined,
    errorDescription: params.get("error_description") ?? undefined,
  }
}
