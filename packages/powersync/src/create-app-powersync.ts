import type { AbstractPowerSyncDatabase } from "@powersync/common"
import { useSyncExternalStore } from "react"
import { connectAppPowerSync, createAppPowerSyncDatabase, disconnectAppPowerSync } from "./client"
import { createAppCollections } from "./collections"
import type { GetAppPowerSyncCredentials } from "./connector"
import { AppSchema, typedStreams } from "./generated/schema"

export type CreateAppPowersyncOptions<
  TDb extends AbstractPowerSyncDatabase = AbstractPowerSyncDatabase,
  TStorage = unknown,
> = {
  database: TStorage
  createDatabase: (options: { schema: typeof AppSchema; database: TStorage }) => TDb
  getCredentials: GetAppPowerSyncCredentials
}

export type AppPowersync<TDb extends AbstractPowerSyncDatabase> = {
  db: TDb
  collections: ReturnType<typeof createAppCollections>
  usersCollection: ReturnType<typeof createAppCollections>["users"]
  userProfilesCollection: ReturnType<typeof createAppCollections>["userProfiles"]
  streams: ReturnType<typeof typedStreams>
  connect: () => Promise<void>
  disconnect: () => Promise<void>
  useSyncReady: () => boolean
}

export function createAppPowersync<
  TDb extends AbstractPowerSyncDatabase = AbstractPowerSyncDatabase,
  TStorage = unknown,
>(options: CreateAppPowersyncOptions<TDb, TStorage>): AppPowersync<TDb> {
  const db = createAppPowerSyncDatabase(options)
  const collections = createAppCollections(db)
  const streams = typedStreams(db)

  let syncReady = false
  const listeners = new Set<() => void>()

  function emit() {
    for (const listener of listeners) listener()
  }

  function subscribe(listener: () => void) {
    listeners.add(listener)
    return () => listeners.delete(listener)
  }

  function getSnapshot() {
    return syncReady
  }

  async function connect() {
    const credentials = await options.getCredentials()
    if (!credentials) {
      syncReady = false
      emit()
      return
    }

    await connectAppPowerSync(db, options.getCredentials)
    syncReady = true
    emit()
  }

  async function disconnect() {
    await disconnectAppPowerSync(db)
    syncReady = false
    emit()
  }

  function useSyncReady() {
    return useSyncExternalStore(subscribe, getSnapshot, () => false)
  }

  return {
    db,
    collections,
    usersCollection: collections.users,
    userProfilesCollection: collections.userProfiles,
    streams,
    connect,
    disconnect,
    useSyncReady,
  }
}
