import * as SecureStore from "expo-secure-store"
import { type StoredOAuthSession, setAccessToken } from "@zula/api"

const SESSION_STORAGE_KEY = "zula.oauth.session"

export async function readStoredSession(): Promise<StoredOAuthSession | null> {
  const raw = await SecureStore.getItemAsync(SESSION_STORAGE_KEY)
  if (!raw) return null
  return JSON.parse(raw) as StoredOAuthSession
}

export async function writeStoredSession(session: StoredOAuthSession | null) {
  if (!session) {
    await SecureStore.deleteItemAsync(SESSION_STORAGE_KEY)
    setAccessToken(null)
    return
  }

  await SecureStore.setItemAsync(SESSION_STORAGE_KEY, JSON.stringify(session))
  setAccessToken(session.accessToken)
}

export async function clearStoredSession() {
  await writeStoredSession(null)
}
