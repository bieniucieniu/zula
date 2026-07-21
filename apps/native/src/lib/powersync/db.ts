import { SQLJSOpenFactory } from "@powersync/adapter-sql-js"
import { PowerSyncDatabase } from "@powersync/react-native"
import { createAppPowerSyncDatabase } from "@zula/powersync"

/**
 * Expo Go requires the sql-js adapter — native SQLite adapters won't load in the sandbox.
 * Switch to OP-SQLite or react-native-quick-sqlite for dev/production builds.
 */
export const powerSyncDb = createAppPowerSyncDatabase({
  database: new SQLJSOpenFactory({
    dbFilename: "app.db",
  }),
  createDatabase: (options) => new PowerSyncDatabase(options),
})
