import type { AbstractPowerSyncDatabase, PowerSyncBackendConnector } from "@powersync/common"
import { AppSchema, type Database } from "./generated/schema"

function isConstructor<Args extends unknown[], Ret>(
  fn: { new (...args: Args): Ret } | ((...args: Args) => Ret)
): fn is { new (...args: Args): Ret } {
  return typeof fn === "function" && /^class\s/.test(Function.prototype.toString.call(fn))
}

export type CreateAppPowersyncOptions<
  TDb extends AbstractPowerSyncDatabase = AbstractPowerSyncDatabase,
> = {
  createDatabase:
    | ((options: { schema: typeof AppSchema }) => TDb)
    | { new (options: { schema: typeof AppSchema }): TDb }
  connector: PowerSyncBackendConnector
}

export type AppPowersync<TDb extends AbstractPowerSyncDatabase> = {
  db: TDb
  schemas: typeof AppSchema
  connect: () => Promise<void>
  disconnect: () => Promise<void>
  getCollectionsOptions: {
    <K extends keyof Database>(
      name: K
    ): {
      database: TDb
      table: (typeof AppSchema)["props"][K]
    }
    <K extends keyof Database, T>(
      name: K,
      extra: T
    ): {
      database: TDb
      table: (typeof AppSchema)["props"][K]
    } & T
  }
}

export function createAppPowersync<
  TDb extends AbstractPowerSyncDatabase = AbstractPowerSyncDatabase,
>(options: CreateAppPowersyncOptions<TDb>): AppPowersync<TDb> {
  const { createDatabase, connector } = options
  const db = isConstructor(createDatabase)
    ? new createDatabase({ schema: AppSchema })
    : createDatabase({ schema: AppSchema })
  return {
    db,
    schemas: AppSchema,
    connect: () => db.connect(connector),
    disconnect: () => db.disconnect(),
    getCollectionsOptions: <K extends keyof Database, T>(name: K, extra?: T) => {
      return {
        database: db,
        table: AppSchema.props[name],
        ...extra,
      }
    },
  }
}
