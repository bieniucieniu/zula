import { describe, expect, it } from "vitest"
import { buildAuthorizeUrl } from "../core/authorize-url"
import { parseOAuthRedirect } from "../core/parse-response"

const provider = {
  id: "apple",
  clientId: "apple-client",
  authorizeUrl: "https://appleid.apple.com/auth/authorize",
  tokenUrl: "https://appleid.apple.com/auth/token",
  scopes: ["name", "email"],
}

describe("buildAuthorizeUrl", () => {
  it("builds an id_token authorize url", () => {
    const url = buildAuthorizeUrl({
      provider,
      redirectUri: "http://localhost:3000/oauth/callback",
      state: "state-123",
      nonce: "nonce-456",
      extraParams: { response_mode: "fragment" },
    })

    expect(url).toContain("client_id=apple-client")
    expect(url).toContain("response_type=id_token")
    expect(url).toContain("state=state-123")
    expect(url).toContain("nonce=nonce-456")
    expect(url).toContain("response_mode=fragment")
  })
})

describe("parseOAuthRedirect", () => {
  it("parses id_token from hash fragment", () => {
    const parsed = parseOAuthRedirect(
      "http://localhost:3000/oauth/callback#id_token=abc&state=state-123"
    )

    expect(parsed.idToken).toBe("abc")
    expect(parsed.state).toBe("state-123")
  })
})
