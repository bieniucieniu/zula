import { PowerSyncDatabase } from "@powersync/web"
import { createAppPowersync, type AppPowerSyncCredentials } from "@zula/powersync"
import { useEffect } from "react"
import { useAuth } from "@/lib/auth"

const defaultApiUrl = "/api/v1"

let accessToken: string | null | undefined

async function getCredentials(): Promise<AppPowerSyncCredentials | null> {
  if (!accessToken) return null

  return {
    endpoint: import.meta.env.VITE_PS_URL ?? "http://127.0.0.1:8080",
    syncBatchUrl: `${import.meta.env.VITE_API_URL ?? defaultApiUrl}/sync/batch`,
    token: accessToken,
  }
}

export const powerSync = createAppPowersync({
  database: {
    dbFilename: "app.db",
  },
  createDatabase: (options) => new PowerSyncDatabase(options),
  getCredentials,
})

export const {
  db: powerSyncDb,
  usersCollection,
  userProfilesCollection,
  streams,
  connect,
  disconnect,
  useSyncReady,
} = powerSync

export const usePowerSync = () => ({ ready: useSyncReady(), db: powerSyncDb })

export function usePowerSyncAuth() {
  const { session } = useAuth()

  useEffect(() => {
    accessToken = session?.accessToken
    if (session?.accessToken) void powerSync.connect()
    else void powerSync.disconnect()
  }, [session?.accessToken])
}
