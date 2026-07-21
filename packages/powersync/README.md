# `@zula/powersync`

Shared PowerSync client for `apps/web` and `apps/native`. Bundles `AppSchema` + sync helpers.

## Scripts

```bash
bun run gen   # needs devenv --profile powersync up
```

## Exports

| Import | Contents |
|--------|----------|
| `@zula/powersync` | `createAppPowersync`, schema, connector, low-level helpers |
| `@zula/powersync/schema` | generated schema only |

## Usage

Each app creates one stable instance — usable inside and outside React:

```ts
import { PowerSyncDatabase } from "@powersync/web"
import { createAppPowersync } from "@zula/powersync"

export const powerSync = createAppPowersync({
  database: { dbFilename: "app.db" }, // or SQLJSOpenFactory for Expo Go
  createDatabase: (options) => new PowerSyncDatabase(options),
  getCredentials: async () => ({
    endpoint: "http://127.0.0.1:8080",
    syncBatchUrl: "/api/v1/sync/batch",
    token: accessToken,
  }),
})

export const {
  db,
  usersCollection,
  userProfilesCollection,
  streams,
  connect,
  disconnect,
  useSyncReady,
} = powerSync
```

Wire auth with a small effect (no provider):

```ts
useEffect(() => {
  if (token) void powerSync.connect()
  else void powerSync.disconnect()
}, [token])
```

Upload path (backend): `POST /api/sync/batch` — see [docs/powersync.md](../../docs/powersync.md).
