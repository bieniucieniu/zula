import { SQLJSOpenFactory } from "@powersync/adapter-sql-js"
import { PowerSyncDatabase } from "@powersync/react-native"
import { createAppPowersync, typedStreams } from "@zula/powersync"
import { createCollection } from "@tanstack/react-db"
import { powerSyncCollectionOptions } from "@tanstack/powersync-db-collection"
import { useSyncExternalStore } from "react"
import { createPowerSyncConnector } from "@/lib/powersync-connector"

const defaultApiUrl = "http://127.0.0.1:8000/api/v1"
const powersyncUrl = process.env.EXPO_PUBLIC_PS_URL ?? "http://127.0.0.1:8080"
const syncBatchUrl = `${process.env.EXPO_PUBLIC_API_URL ?? defaultApiUrl}/sync/batch`

let accessToken: string | null | undefined
let syncReady = false
const syncListeners = new Set<() => void>()

function setSyncReady(ready: boolean) {
  syncReady = ready
  for (const listener of syncListeners) listener()
}

function subscribeSyncReady(listener: () => void) {
  syncListeners.add(listener)
  return () => syncListeners.delete(listener)
}

async function getAccessToken() {
  return accessToken ?? null
}

const connector = createPowerSyncConnector({
  getAccessToken,
  powersyncUrl,
  syncBatchUrl,
})

/**
 * Expo Go requires the sql-js adapter — native SQLite adapters won't load in the sandbox.
 * Switch to OP-SQLite or react-native-quick-sqlite for dev/production builds.
 */
export const powerSync = createAppPowersync({
  createDatabase: (options) =>
    new PowerSyncDatabase({
      schema: options.schema,
      database: new SQLJSOpenFactory({
        dbFilename: "app.db",
      }),
    }),
  connector,
})

export const { db: powerSyncDb, connect, disconnect } = powerSync
export const streams = typedStreams(powerSyncDb)

export const usersCollection = createCollection(
  powerSyncCollectionOptions(powerSync.getCollectionsOptions("users"))
)

export const userProfilesCollection = createCollection(
  powerSyncCollectionOptions(powerSync.getCollectionsOptions("user_profiles"))
)

export function usePowerSync() {
  const ready = useSyncExternalStore(
    subscribeSyncReady,
    () => syncReady,
    () => false
  )
  return { ready, db: powerSyncDb }
}

export function setPowerSyncAccessToken(token: string | null | undefined) {
  accessToken = token

  void (async () => {
    if (!token) {
      setSyncReady(false)
      await powerSync.disconnect()
      return
    }

    await powerSync.connect()
    await streams.me().subscribe()
    setSyncReady(true)
  })()
}
