import { SQLJSOpenFactory } from "@powersync/adapter-sql-js"
import { PowerSyncDatabase } from "@powersync/react-native"
import { createAppPowersync, type AppPowerSyncCredentials } from "@zula/powersync"

const defaultApiUrl = "http://127.0.0.1:8000/api/v1"

let accessToken: string | null | undefined

async function getCredentials(): Promise<AppPowerSyncCredentials | null> {
  if (!accessToken) return null

  return {
    endpoint: process.env.EXPO_PUBLIC_PS_URL ?? "http://127.0.0.1:8080",
    syncBatchUrl: `${process.env.EXPO_PUBLIC_API_URL ?? defaultApiUrl}/sync/batch`,
    token: accessToken,
  }
}

/**
 * Expo Go requires the sql-js adapter — native SQLite adapters won't load in the sandbox.
 * Switch to OP-SQLite or react-native-quick-sqlite for dev/production builds.
 */
export const powerSync = createAppPowersync({
  database: new SQLJSOpenFactory({
    dbFilename: "app.db",
  }),
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

export function setPowerSyncAccessToken(token: string | null | undefined) {
  accessToken = token
  if (token) void powerSync.connect()
  else void powerSync.disconnect()
}
