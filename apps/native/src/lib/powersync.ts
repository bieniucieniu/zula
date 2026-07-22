import { SQLJSOpenFactory } from "@powersync/adapter-sql-js"
import { PowerSyncDatabase } from "@powersync/react-native"
import { powerSyncCollectionOptions } from "@tanstack/powersync-db-collection"
import { createCollection } from "@tanstack/react-db"
import { useSyncExternalStore } from "react"
import { AppSchema, typedStreams } from "@/gen/powersync/schema"
import { createPowerSyncConnector } from "@/lib/powersync-connector"

const defaultApiUrl = "http://127.0.0.1:8000/api"
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
export const db = new PowerSyncDatabase({
  schema: AppSchema,
  database: new SQLJSOpenFactory({
    dbFilename: "app.db",
  }),
})

export const powerSyncDb = db
export const streams = typedStreams(db)

export const usersCollection = createCollection(
  powerSyncCollectionOptions({
    database: db,
    table: AppSchema.props.users,
  })
)

export const userProfilesCollection = createCollection(
  powerSyncCollectionOptions({
    database: db,
    table: AppSchema.props.user_profiles,
  })
)

export function connect() {
  return db.connect(connector)
}

export function disconnect() {
  return db.disconnect()
}

export function usePowerSync() {
  const ready = useSyncExternalStore(
    subscribeSyncReady,
    () => syncReady,
    () => false
  )
  return { ready, db }
}

export function setPowerSyncAccessToken(token: string | null | undefined) {
  accessToken = token

  void (async () => {
    if (!token) {
      setSyncReady(false)
      await db.disconnect()
      return
    }

    await db.connect(connector)
    setSyncReady(true)
  })()
}
