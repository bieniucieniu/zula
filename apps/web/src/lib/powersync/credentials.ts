import type { AppPowerSyncCredentials } from "@zula/powersync"

const defaultApiUrl = "/api/v1"

export async function getAppPowerSyncCredentials(
  accessToken: string | null | undefined
): Promise<AppPowerSyncCredentials | null> {
  if (!accessToken) return null

  return {
    endpoint: import.meta.env.VITE_PS_URL ?? "http://127.0.0.1:8080",
    syncBatchUrl: `${import.meta.env.VITE_API_URL ?? defaultApiUrl}/sync/batch`,
    token: accessToken,
  }
}
