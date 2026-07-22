import { PowerSyncDatabase } from "@powersync/web"
import { powerSyncCollectionOptions } from "@tanstack/powersync-db-collection"
import { createCollection } from "@tanstack/react-db"
import { useEffect, useSyncExternalStore } from "react"
import { AppSchema, typedStreams } from "@/gen/powersync/schema"
import { fetchPowerSyncToken } from "@/lib/api-client"
import { useAuth } from "@/lib/auth"
import { createPowerSyncConnector } from "@/lib/powersync-connector"

const defaultApiUrl = "/api"
const powersyncUrl = import.meta.env.VITE_PS_URL ?? "http://127.0.0.1:8080"
const syncBatchUrl = `${import.meta.env.VITE_API_URL ?? defaultApiUrl}/sync/batch`

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

const connector = createPowerSyncConnector({
  getAccessToken: fetchPowerSyncToken,
  powersyncUrl,
  syncBatchUrl,
})

export const db = new PowerSyncDatabase({
  schema: AppSchema,
  database: { dbFilename: "app.db" },
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
