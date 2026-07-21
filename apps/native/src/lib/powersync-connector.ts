import type {
  AbstractPowerSyncDatabase,
  CrudEntry,
  PowerSyncBackendConnector,
} from "@powersync/react-native"

export type PowerSyncConnectorConfig = {
  getAccessToken: () => Promise<string | null>
  powersyncUrl: string
  syncBatchUrl: string
}

export function createPowerSyncConnector(
  config: PowerSyncConnectorConfig
): PowerSyncBackendConnector {
  return {
    async fetchCredentials() {
      const token = await config.getAccessToken()
      if (!token) return null

      return {
        endpoint: config.powersyncUrl,
        token,
      }
    },

    async uploadData(database: AbstractPowerSyncDatabase) {
      const token = await config.getAccessToken()
      if (!token) {
        throw new Error("Cannot upload sync batch without access token")
      }

      const batch = await database.getCrudBatch(100)
      if (!batch) return

      const response = await fetch(config.syncBatchUrl, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          ops: batch.crud.map((entry: CrudEntry) => ({
            clientId: entry.clientId,
            op: entry.op,
            table: entry.table,
            id: entry.id,
            opData: entry.opData ?? null,
            metadata: entry.metadata ?? null,
          })),
        }),
      })

      if (!response.ok) {
        throw new Error(`Sync upload failed with status ${response.status}`)
      }

      await batch.complete()
    },
  }
}
