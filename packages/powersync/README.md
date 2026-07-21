# `@zula/powersync`

Shared PowerSync client for `apps/web` and `apps/native`. Bundles `AppSchema` + sync helpers.

## Scripts

```bash
bun run gen   # needs devenv --profile powersync up
```

## Exports

| Import | Contents |
|--------|----------|
| `@zula/powersync` | schema, `createAppPowerSyncDatabase`, `createAppCollections`, connector, connect helpers |
| `@zula/powersync/react` | `PowerSyncProvider`, `usePowerSync` |
| `@zula/powersync/schema` | generated schema only |

## App integration

Each platform provides **storage** + **credentials**. Everything else lives here.

```ts
// db.ts — platform-specific storage
import { PowerSyncDatabase } from "@powersync/web" // or @powersync/react-native
import { createAppPowerSyncDatabase } from "@zula/powersync"

export const powerSyncDb = createAppPowerSyncDatabase({
  database: { dbFilename: "app.db" }, // or SQLJSOpenFactory for Expo Go
  createDatabase: (options) => new PowerSyncDatabase(options),
})
```

```ts
// credentials.ts — env + auth token
import type { AppPowerSyncCredentials } from "@zula/powersync"

export async function getAppPowerSyncCredentials(
  accessToken: string | null | undefined,
): Promise<AppPowerSyncCredentials | null> {
  if (!accessToken) return null
  return {
    endpoint: "http://127.0.0.1:8080",
    syncBatchUrl: "/api/v1/sync/batch",
    token: accessToken,
  }
}
```

```tsx
// provider.tsx
import { PowerSyncProvider } from "@zula/powersync/react"
import { createAppCollections } from "@zula/powersync"

const collections = createAppCollections(powerSyncDb)

<PowerSyncProvider db={powerSyncDb} getCredentials={getCredentials}>
  {children}
</PowerSyncProvider>
```

Upload path (backend): `POST /api/sync/batch` — see [docs/powersync.md](../../docs/powersync.md).
