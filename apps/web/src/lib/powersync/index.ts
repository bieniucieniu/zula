import { createAppCollections } from "@zula/powersync"
import { powerSyncDb } from "./db"

const collections = createAppCollections(powerSyncDb)

export const usersCollection = collections.users
export const userProfilesCollection = collections.userProfiles
export { powerSyncDb } from "./db"
export { PowerSyncAuthBridge, usePowerSync } from "./provider"
