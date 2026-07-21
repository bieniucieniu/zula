import type { AbstractPowerSyncDatabase } from "@powersync/common"
import { createCollection } from "@tanstack/react-db"
import { powerSyncCollectionOptions } from "@tanstack/powersync-db-collection"
import { AppSchema } from "./generated/schema"

export function createAppCollections(db: AbstractPowerSyncDatabase) {
  return {
    users: createCollection(
      powerSyncCollectionOptions({
        database: db,
        table: AppSchema.props.users,
      })
    ),
    userProfiles: createCollection(
      powerSyncCollectionOptions({
        database: db,
        table: AppSchema.props.user_profiles,
      })
    ),
  }
}
