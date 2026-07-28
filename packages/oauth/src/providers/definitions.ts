import type { OAuthProviderDefinition } from "../types"

/** Static client-side extras (labels, authorize params). Not a plugin registry. */
export const PROVIDER_DEFINITIONS: Record<string, OAuthProviderDefinition> = {
  google: {
    id: "google",
    label: "Google",
    extraAuthParams: {
      access_type: "offline",
      prompt: "consent",
      include_granted_scopes: "true",
    },
  },
  apple: {
    id: "apple",
    label: "Apple",
    extraAuthParams: { response_mode: "fragment" },
  },
  /** Local bypass when server lists `dev` on GET /auth/providers. */
  dev: {
    id: "dev",
    label: "Dev",
  },
}

export function getProviderDefinition(providerId: string): OAuthProviderDefinition | undefined {
  return PROVIDER_DEFINITIONS[providerId]
}
