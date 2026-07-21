import { column, type PowerSyncDatabase, Schema, type SyncStream, Table } from "@powersync/web"

// OR: import { column, Schema, Table, PowerSyncDatabase, SyncStream } from '@powersync/react-native';

const users = new Table(
  {
    // id column (text) is automatically included
    username: column.text,
  },
  { indexes: {} }
)

const user_profiles = new Table(
  {
    // id column (text) is automatically included
    display_name: column.text,
    avatar_url: column.text,
    bio: column.text,
    timezone: column.text,
    preferred_language: column.text,
    location_tag: column.text,
    seller_headline: column.text,
    updated_at: column.integer,
  },
  { indexes: {} }
)

export const AppSchema = new Schema({
  users,
  user_profiles,
})

export type Database = (typeof AppSchema)["types"]

export function typedStreams(db: PowerSyncDatabase) {
  return {
    userProfile(params: { user_id: string }): SyncStream {
      return db.syncStream("user_profile", params)
    },
  }
}
