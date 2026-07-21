import { createCollection } from "@tanstack/react-db"
import { powerSyncCollectionOptions } from "@tanstack/powersync-db-collection"
import { AppSchema } from "@zula/powersync"
import { powerSyncDb } from "./db"

export const usersCollection = createCollection(
  powerSyncCollectionOptions({
    database: powerSyncDb,
    table: AppSchema.props.users,
  })
)

export const userProfilesCollection = createCollection(
  powerSyncCollectionOptions({
    database: powerSyncDb,
    table: AppSchema.props.user_profiles,
  })
)
