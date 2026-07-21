import type { AppPowerSyncCredentials } from "@zula/powersync"

const defaultApiUrl = "http://127.0.0.1:8000/api/v1"

export async function getAppPowerSyncCredentials(
  accessToken: string | null | undefined
): Promise<AppPowerSyncCredentials | null> {
  if (!accessToken) return null

  return {
    endpoint: process.env.EXPO_PUBLIC_PS_URL ?? "http://127.0.0.1:8080",
    syncBatchUrl: `${process.env.EXPO_PUBLIC_API_URL ?? defaultApiUrl}/sync/batch`,
    token: accessToken,
  }
}
