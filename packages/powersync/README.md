# `@zula/powersync`

Shared PowerSync client schema + data-source helpers for `apps/web` and `apps/native`.

## Scripts

```bash
bun run gen   # needs devenv --profile powersync up
```

Writes `src/generated/schema.ts` from `powersync/sync-config.yaml` + live instance.

## Exports

| Import | Contents |
|--------|----------|
| `@zula/powersync` | `AppSchema`, `typedStreams`, `resolveDataSourceConfig` |
| `@zula/powersync/schema` | generated schema only |
| `@zula/powersync/data-source` | API fallback modes |

Platform adapters (`@powersync/web`, `@powersync/react-native`) stay in apps — this package only shares schema + policy helpers.

## API fallback

See `DataSourceMode` / `resolveDataSourceConfig` and [docs/powersync.md](../../docs/powersync.md).
