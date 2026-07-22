import type {
  AbstractPowerSyncDatabase,
  PowerSyncBackendConnector,
} from "@powersync/react-native"
import { ProblemDetailsError } from "@zula/api"
import { syncBatch } from "@zula/api/endpoints"

export type PowerSyncConnectorConfig = {
  getAccessToken: () => Promise<string | null>
  powersyncUrl: string
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
      const batch = await database.getCrudBatch(100)
      if (!batch) return

      const { data } = await syncBatch({
        ops: batch.crud.map((entry) => ({
          clientId: entry.clientId,
          op: entry.op,
          table: entry.table,
          id: entry.id,
          opData: entry.opData ?? null,
          metadata: entry.metadata ?? null,
        })),
      })

      data.results.map((result) => {
        if (result.problem != null) {
          throw new ProblemDetailsError(result.problem)
        }
      })

      await batch.complete()
    },
  }
}
