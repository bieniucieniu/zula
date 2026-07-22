import { SQLJSOpenFactory } from "@powersync/adapter-sql-js"
import { PowerSyncDatabase } from "@powersync/react-native"
import { powerSyncCollectionOptions } from "@tanstack/powersync-db-collection"
import { createCollection } from "@tanstack/react-db"
import { getPowerSyncToken } from "@zula/api/endpoints"
import { useEffect, useSyncExternalStore } from "react"
import { AppSchema, typedStreams } from "@/gen/powersync/schema"
import { useAuth } from "@/lib/auth"
import { createPowerSyncConnector } from "@/lib/powersync-connector"

const powersyncUrl = process.env.EXPO_PUBLIC_PS_URL ?? "http://127.0.0.1:8080"

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

async function fetchPowerSyncAccessToken(): Promise<string | null> {
  try {
    const { data } = await getPowerSyncToken()
    return data.accessToken
  } catch {
    return null
  }
}

const connector = createPowerSyncConnector({
  getAccessToken: fetchPowerSyncAccessToken,
  powersyncUrl,
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

db.connect(connector)

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

export function usePowerSync() {
  const ready = useSyncExternalStore(
    subscribeSyncReady,
    () => syncReady,
    () => false
  )
  return { ready, db }
}

export function usePowerSyncAuth() {
  const { session } = useAuth()

  useEffect(() => {
    let cancelled = false

    async function run() {
      if (!session) {
        setSyncReady(false)
        await db.disconnect()
        return
      }

      await db.connect(connector)
      if (!cancelled) setSyncReady(true)
    }

    void run()

    return () => {
      cancelled = true
      setSyncReady(false)
      void db.disconnect()
    }
  }, [session])
}
