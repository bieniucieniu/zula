/**
 * Bootstrap AppSchema matching powersync/sync-config.yaml.
 * Overwritten by `bun run gen` when PowerSync Service is reachable.
 *
 * Uses @powersync/common so web (@powersync/web) and native
 * (@powersync/react-native) can share the same schema.
 */
import { column, Schema, Table } from "@powersync/common"

const users = new Table({
  username: column.text,
})

const user_profiles = new Table({
  display_name: column.text,
  avatar_url: column.text,
  bio: column.text,
  timezone: column.text,
  preferred_language: column.text,
  location_tag: column.text,
  seller_headline: column.text,
  updated_at: column.integer,
})

export const AppSchema = new Schema({
  users,
  user_profiles,
})

export type Database = (typeof AppSchema)["types"]
export type UserRecord = Database["users"]
export type UserProfileRecord = Database["user_profiles"]

/** Typed stream helpers (manual until CLI regenerates wrappers). */
export function typedStreams(db: {
  syncStream: (
    name: string,
    params?: Record<string, string>
  ) => {
    subscribe: (opts?: { ttl?: number }) => Promise<{ unsubscribe: () => void }>
  }
}) {
  return {
    me: () => db.syncStream("me", {}),
    userProfile: (params: { user_id: string }) =>
      db.syncStream("user_profile", { user_id: params.user_id }),
  }
}
