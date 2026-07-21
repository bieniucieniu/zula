import type {
  AbstractPowerSyncDatabase,
  CrudEntry,
  PowerSyncBackendConnector,
} from "@powersync/common"

export type AppPowerSyncCredentials = {
  endpoint: string
  token: string
  syncBatchUrl: string
}

export type GetAppPowerSyncCredentials = () => Promise<AppPowerSyncCredentials | null>

export type AppBackendConnectorOptions = {
  getCredentials: GetAppPowerSyncCredentials
}

function serializeCrudEntry(entry: CrudEntry) {
  return {
    clientId: entry.clientId,
    op: entry.op,
    table: entry.table,
    id: entry.id,
    opData: entry.opData ?? null,
    metadata: entry.metadata ?? null,
  }
}

export function createAppBackendConnector(
  options: AppBackendConnectorOptions
): PowerSyncBackendConnector {
  const { getCredentials } = options

  return {
    async fetchCredentials() {
      const credentials = await getCredentials()
      if (!credentials) return null

      return {
        endpoint: credentials.endpoint,
        token: credentials.token,
      }
    },

    async uploadData(database: AbstractPowerSyncDatabase) {
      const credentials = await getCredentials()
      if (!credentials) {
        throw new Error("Cannot upload sync batch without credentials")
      }

      const batch = await database.getCrudBatch(100)
      if (!batch) return

      const response = await fetch(credentials.syncBatchUrl, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${credentials.token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          ops: batch.crud.map(serializeCrudEntry),
        }),
      })

      if (!response.ok) {
        throw new Error(`Sync upload failed with status ${response.status}`)
      }

      await batch.complete()
    },
  }
}
