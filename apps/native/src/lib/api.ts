import { setApiAuthMode, setApiBaseUrl } from "@zula/api"

const defaultApiUrl = "http://127.0.0.1:8000/api"

function sanitizeUrl(url: string) {
  if (url.startsWith("http://") || url.startsWith("https://")) return url
  return "http://" + url
}

export function configureApiClient() {
  setApiBaseUrl(sanitizeUrl(process.env.EXPO_PUBLIC_API_URL ?? defaultApiUrl))
  setApiAuthMode("bearer")
}

configureApiClient()
