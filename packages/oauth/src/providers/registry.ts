import type { OAuthProviderDefinition } from "../types"

const definitions = new Map<string, OAuthProviderDefinition>([
  [
    "google",
    {
      id: "google",
      label: "Google",
      web: {
        strategy: "google-identity",
        scriptUrl: "https://accounts.google.com/gsi/client",
      },
      native: {
        strategy: "expo-id-token",
        clientIdEnv: {
          web: "EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID",
          ios: "EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID",
          android: "EXPO_PUBLIC_GOOGLE_ANDROID_CLIENT_ID",
          fallback: "EXPO_PUBLIC_GOOGLE_CLIENT_ID",
        },
      },
    },
  ],
  [
    "apple",
    {
      id: "apple",
      label: "Apple",
      web: {
        strategy: "popup",
        extraAuthParams: {
          response_mode: "fragment",
        },
      },
      native: {
        strategy: "expo-id-token",
        clientIdEnv: {
          fallback: "EXPO_PUBLIC_APPLE_CLIENT_ID",
        },
      },
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
