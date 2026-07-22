import * as SecureStore from "expo-secure-store"

const ACCESS_KEY = "zula_access"
const REFRESH_KEY = "zula_refresh"

let memoryAccessToken: string | null = null
let memoryRefreshToken: string | null = null

export function getMemoryAccessToken() {
  return memoryAccessToken
}

export function getMemoryRefreshToken() {
  return memoryRefreshToken
}

export async function loadTokens() {
  const [access, refresh] = await Promise.all([
    SecureStore.getItemAsync(ACCESS_KEY),
    SecureStore.getItemAsync(REFRESH_KEY),
  ])
  memoryAccessToken = access
  memoryRefreshToken = refresh
  return { accessToken: access, refreshToken: refresh }
}

export async function saveTokens(accessToken: string, refreshToken?: string | null) {
  memoryAccessToken = accessToken
  await SecureStore.setItemAsync(ACCESS_KEY, accessToken)
  if (refreshToken) {
    memoryRefreshToken = refreshToken
    await SecureStore.setItemAsync(REFRESH_KEY, refreshToken)
  }
}

export async function clearTokens() {
  memoryAccessToken = null
  memoryRefreshToken = null
  await Promise.all([
    SecureStore.deleteItemAsync(ACCESS_KEY),
    SecureStore.deleteItemAsync(REFRESH_KEY),
  ])
}
