const defaultApiUrl = "/api/v1"

export const powersyncConfig = {
  powersyncUrl: import.meta.env.VITE_PS_URL ?? "http://127.0.0.1:8080",
  syncBatchUrl: `${import.meta.env.VITE_API_URL ?? defaultApiUrl}/sync/batch`,
}
