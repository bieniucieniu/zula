import type { AbstractPowerSyncDatabase, PowerSyncBackendConnector } from "@powersync/web"
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
      const batch = await database.getNextCrudTransaction()
      if (!batch) return

      const out = await syncBatch({ ops: batch.crud })
      out.data.results.map((result) => {
        if (result.problem != null) {
          throw new ProblemDetailsError(result.problem)
        }
      })

      await batch.complete()
    },
  }
}
