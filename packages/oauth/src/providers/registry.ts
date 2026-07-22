import type { OAuthProviderDefinition } from "../types"

const definitions = new Map<string, OAuthProviderDefinition>([
  [
    "google",
    {
      id: "google",
      label: "Google",
      extraAuthParams: { response_mode: "fragment", prompt: "select_account" },
    },
  ],
  [
    "apple",
    {
      id: "apple",
      label: "Apple",
      extraAuthParams: { response_mode: "fragment" },
    },
  ],
])

export function getProviderDefinition(providerId: string): OAuthProviderDefinition | undefined {
  return definitions.get(providerId)
}

export function registerProviderDefinition(definition: OAuthProviderDefinition) {
  definitions.set(definition.id, definition)
}

export function listProviderDefinitions(): OAuthProviderDefinition[] {
  return [...definitions.values()]
}
