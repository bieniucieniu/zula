import { PowerSyncDatabase } from "@powersync/web"
import { AppSchema } from "@zula/powersync"

export const powerSyncDb = new PowerSyncDatabase({
  schema: AppSchema,
  database: {
    dbFilename: "app.db",
  },
})
