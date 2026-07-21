const defaultApiUrl = "http://127.0.0.1:8000/api/v1"

export const powersyncConfig = {
  powersyncUrl: process.env.EXPO_PUBLIC_PS_URL ?? "http://127.0.0.1:8080",
  syncBatchUrl: `${process.env.EXPO_PUBLIC_API_URL ?? defaultApiUrl}/sync/batch`,
}
