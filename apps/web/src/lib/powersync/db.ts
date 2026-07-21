import { PowerSyncDatabase } from "@powersync/web"
import { createAppPowerSyncDatabase } from "@zula/powersync"

export const powerSyncDb = createAppPowerSyncDatabase({
  database: {
    dbFilename: "app.db",
  },
  createDatabase: (options) => new PowerSyncDatabase(options),
})
