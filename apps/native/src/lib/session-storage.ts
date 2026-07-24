import AsyncStorage from "@react-native-async-storage/async-storage"
import { type StoredOAuthSession, setAccessToken } from "@zula/api"

const SESSION_STORAGE_KEY = "zula.oauth.session"

export async function readStoredSession(): Promise<StoredOAuthSession | null> {
  const raw = await AsyncStorage.getItem(SESSION_STORAGE_KEY)
  if (!raw) return null
  return JSON.parse(raw) as StoredOAuthSession
}

export async function writeStoredSession(session: StoredOAuthSession | null) {
  if (!session) {
    await AsyncStorage.removeItem(SESSION_STORAGE_KEY)
    setAccessToken(null)
    return
  }

  await AsyncStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session))
  setAccessToken(session.accessToken)
}

export async function clearStoredSession() {
  await writeStoredSession(null)
}
