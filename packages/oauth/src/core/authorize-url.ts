import type { BuildAuthorizeUrlInput } from "../types"

export function buildAuthorizeUrl({
  provider,
  redirectUri,
  state,
  nonce,
  responseType = "id_token",
  extraParams = {},
}: BuildAuthorizeUrlInput): string {
  const url = new URL(provider.authorizeUrl)
  url.searchParams.set("client_id", provider.clientId)
  url.searchParams.set("redirect_uri", redirectUri)
  url.searchParams.set("response_type", responseType)
  url.searchParams.set("scope", provider.scopes.join(" "))
  url.searchParams.set("state", state)
  url.searchParams.set("nonce", nonce)

  for (const [key, value] of Object.entries(extraParams)) {
    url.searchParams.set(key, value)
  }

  return url.toString()
}
