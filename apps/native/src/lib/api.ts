import {
  authenticateWithCode,
  authenticateWithDevBypass,
  createApiClient,
  setDefaultApiClient,
  toStoredOAuthSession,
} from "@zula/api"
import { refresh as apiRefresh } from "@zula/api/endpoints"
import { readStoredSession, writeStoredSession } from "@/lib/session-storage"

const defaultApiUrl = "http://127.0.0.1:8000/api"

function sanitizeUrl(url: string) {
  if (url.startsWith("http://") || url.startsWith("https://")) return url
  return "http://" + url
}

export function configureApiClient() {
  const client = createApiClient({
    baseUrl: sanitizeUrl(process.env.EXPO_PUBLIC_API_URL ?? defaultApiUrl),
    authMode: "bearer",
    onUnauthorized: async () => {
      const stored = await readStoredSession()
      if (!stored?.refreshToken) return false

      try {
        const { data } = await apiRefresh({ data: { refreshToken: stored.refreshToken } })
        await writeStoredSession(toStoredOAuthSession(data))
        return true
      } catch {
        await writeStoredSession(null)
        return false
      }
    },
  })
  setDefaultApiClient(client)
}

export async function authenticateNativeCode(input: {
  provider: string
  code: string
  codeVerifier?: string | null
  redirectUri: string
}) {
  const next = await authenticateWithCode({
    provider: input.provider,
    code: input.code,
    codeVerifier: input.codeVerifier,
    redirectUri: input.redirectUri,
    deviceInfo: "native",
  })
  await writeStoredSession(next)
  return next
}

export async function authenticateNativeDevBypass(secret: string) {
  const next = await authenticateWithDevBypass({ secret })
  await writeStoredSession(next)
  return next
}
