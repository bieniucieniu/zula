# zula

Monorepo: Ktor API server + React SPA.

## Structure

```text
zula/
├── apps/web/     # Vite + React frontend
├── server/       # Ktor backend
└── gradle/       # Shared Gradle version catalog
```

## Building & Running

### Server (API)

| Task | Description |
|------|-------------|
| `./gradlew :server:run` | Run the API server on port 8080 |
| `./gradlew :server:build` | Build the server JAR |
| `./gradlew :server:test` | Run server tests |

API routes are under `/api`. OAuth login/callback stay at `/login` and `/callback`.

### Web (SPA)

```bash
npm install
npm run dev -w apps/web
```

Vite dev server runs on port 5173 and proxies `/api`, `/login`, and `/callback` to the Ktor server.

## Links

* [Ktor Documentation](https://ktor.io/docs/home.html)
* [Vite Documentation](https://vite.dev/)
