import type { AbstractPowerSyncDatabase } from "@powersync/common"
import { createAppBackendConnector, type GetAppPowerSyncCredentials } from "./connector"
import { AppSchema, typedStreams } from "./generated/schema"

export type CreateAppPowerSyncDatabaseOptions<
  TDb extends AbstractPowerSyncDatabase = AbstractPowerSyncDatabase,
  TStorage = unknown,
> = {
  /** Platform-specific storage: web `{ dbFilename }`, native `SQLJSOpenFactory`, etc. */
  database: TStorage
  createDatabase: (options: { schema: typeof AppSchema; database: TStorage }) => TDb
}

export function createAppPowerSyncDatabase<
  TDb extends AbstractPowerSyncDatabase = AbstractPowerSyncDatabase,
  TStorage = unknown,
>(options: CreateAppPowerSyncDatabaseOptions<TDb, TStorage>): TDb {
  return options.createDatabase({
    schema: AppSchema,
    database: options.database,
  })
}

export async function connectAppPowerSync(
  db: AbstractPowerSyncDatabase,
  getCredentials: GetAppPowerSyncCredentials
): Promise<void> {
  const credentials = await getCredentials()
  if (!credentials) return

  const connector = createAppBackendConnector({ getCredentials })
  await db.connect(connector)
  await typedStreams(db).me().subscribe()
}

export async function disconnectAppPowerSync(
  db: AbstractPowerSyncDatabase
): Promise<void> {
  await db.disconnect()
}
