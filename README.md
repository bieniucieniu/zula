# zula

Monorepo: Ktor API + React web + Expo native.

**Product:** social marketplace for small services, craft batches, and import/resell communities — see [docs/product_vision.md](./docs/product_vision.md).

## Structure

```text
zula/
├── apps/web/     # Vite + React frontend
├── apps/native/  # Expo
├── packages/     # @zula/api, @zula/oauth
├── server/       # Ktor backend
├── docs/         # Module guides + product vision
└── gradle/       # Shared Gradle version catalog
```

## Building & Running

### Server (API)

| Task                      | Description                     |
|---------------------------|---------------------------------|
| `./gradlew :server:run`   | Run the API server on port 8000 |
| `./gradlew :server:build` | Build the server JAR            |
| `./gradlew :server:test`  | Run server tests                |

API routes are under `/api`.

```bash
cd apps/web && bun run dev
cd apps/native && bun run dev
```

Vite proxies `/api` to the Ktor server.

## Docs

Start at [docs/README.md](./docs/README.md) → [product vision](./docs/product_vision.md) → [implementation plan](./docs/implementation_plan.md).

## Links

* [Ktor Documentation](https://ktor.io/docs/home.html)
* [Vite Documentation](https://vite.dev/)
