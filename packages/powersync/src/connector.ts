import type {
  AbstractPowerSyncDatabase,
  CrudEntry,
  PowerSyncBackendConnector,
} from "@powersync/common"

export type AppBackendConnectorOptions = {
  powersyncUrl: string
  syncBatchUrl: string
  getAccessToken: () => Promise<string | null>
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
  const { powersyncUrl, syncBatchUrl, getAccessToken } = options

  return {
    async fetchCredentials() {
      const token = await getAccessToken()
      if (!token) return null

      return {
        endpoint: powersyncUrl,
        token,
      }
    },

    async uploadData(database: AbstractPowerSyncDatabase) {
      const accessToken = await getAccessToken()
      if (!accessToken) {
        throw new Error("Cannot upload sync batch without an access token")
      }

      const batch = await database.getCrudBatch(100)
      if (!batch) return

      const response = await fetch(syncBatchUrl, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
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
