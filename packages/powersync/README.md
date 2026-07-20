# `@zula/powersync`

Shared PowerSync client schema for `apps/web` and `apps/native`.

## Scripts

```bash
bun run gen   # needs devenv --profile powersync up
```

Runs:

```text
powersync generate schema --output=ts --output-path=src/generated/schema.ts \
  --directory=../../powersync --api-url=http://127.0.0.1:8080
```

## Exports

| Import | Contents |
|--------|----------|
| `@zula/powersync` | `AppSchema`, `typedStreams` |
| `@zula/powersync/schema` | generated schema only |

Platform adapters (`@powersync/web`, `@powersync/react-native`) stay in apps.

Upload path (backend): `POST /api/sync/batch` — see [docs/powersync.md](../../docs/powersync.md).
