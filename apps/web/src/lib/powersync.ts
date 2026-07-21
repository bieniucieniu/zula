import { PowerSyncDatabase } from "@powersync/web"
import { createAppPowersync, typedStreams } from "@zula/powersync"
import { createCollection } from "@tanstack/react-db"
import { powerSyncCollectionOptions } from "@tanstack/powersync-db-collection"
import { useEffect, useSyncExternalStore } from "react"
import { useAuth } from "@/lib/auth"
import { createPowerSyncConnector } from "@/lib/powersync-connector"

const defaultApiUrl = "/api/v1"
const powersyncUrl = import.meta.env.VITE_PS_URL ?? "http://127.0.0.1:8080"
const syncBatchUrl = `${import.meta.env.VITE_API_URL ?? defaultApiUrl}/sync/batch`

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

export const powerSync = createAppPowersync({
  createDatabase: (options) =>
    new PowerSyncDatabase({
      schema: options.schema,
      database: { dbFilename: "app.db" },
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

export function usePowerSyncAuth() {
  const { session } = useAuth()

  useEffect(() => {
    let cancelled = false

    accessToken = session?.accessToken

    async function run() {
      if (!session?.accessToken) {
        setSyncReady(false)
        await powerSync.disconnect()
        return
      }

      await powerSync.connect()
      await streams.me().subscribe()
      if (!cancelled) setSyncReady(true)
    }

    void run()

    return () => {
      cancelled = true
      setSyncReady(false)
      void powerSync.disconnect()
    }
  }, [session?.accessToken])
}
