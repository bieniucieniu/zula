/**
 * How clients should load data when PowerSync may be unavailable.
 *
 * Wire app code to prefer PowerSync when connected; fall back to REST
 * (`@zula/api`) for reads when sync is down / not yet first-synced.
 *
 * Options to implement at call sites:
 * - `powersync` — local SQLite via PowerSync only (offline-first screens)
 * - `api` — REST only (ignore local DB)
 * - `powersync-with-api-fallback` — try PowerSync; on disconnect / error / missing
 *   first sync, fetch from API base (`API_URL` / Orval client)
 *
 * Env: `POWERSYNC_URL` (e.g. http://127.0.0.1:8081). Empty/unset → treat as api-only.
 */
export type DataSourceMode =
  | "powersync"
  | "api"
  | "powersync-with-api-fallback"

export type DataSourceConfig = {
  mode: DataSourceMode
  /** PowerSync Service HTTP origin (no path). */
  powersyncUrl: string | null
  /** REST API base (`…/api/v1`). */
  apiUrl: string
}

export function resolveDataSourceConfig(env: {
  POWERSYNC_URL?: string
  API_URL?: string
  VITE_POWERSYNC_URL?: string
  VITE_API_URL?: string
}): DataSourceConfig {
  const powersyncUrl =
    emptyToNull(env.POWERSYNC_URL) ?? emptyToNull(env.VITE_POWERSYNC_URL)
  const apiUrl =
    emptyToNull(env.API_URL) ??
    emptyToNull(env.VITE_API_URL) ??
    "http://localhost:8080/api/v1"

  if (!powersyncUrl) {
    return { mode: "api", powersyncUrl: null, apiUrl }
  }

  return {
    mode: "powersync-with-api-fallback",
    powersyncUrl,
    apiUrl,
  }
}

function emptyToNull(value: string | undefined): string | null {
  if (value == null || value.trim() === "") return null
  return value.trim()
}
