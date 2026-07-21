import { SQLJSOpenFactory } from "@powersync/adapter-sql-js"
import { PowerSyncDatabase } from "@powersync/react-native"
import { AppSchema } from "@zula/powersync"

/**
 * Expo Go requires the sql-js adapter — native SQLite adapters won't load in the sandbox.
 * Switch to OP-SQLite or react-native-quick-sqlite for dev/production builds.
 */
export const powerSyncDb = new PowerSyncDatabase({
  schema: AppSchema,
  database: new SQLJSOpenFactory({
    dbFilename: "zula.db",
  }),
})
