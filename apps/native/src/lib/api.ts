import { setApiAuthMode, setApiBaseUrl } from "@zula/api"

const defaultApiUrl = "http://127.0.0.1:8000/api"

export function configureApiClient() {
  setApiBaseUrl(process.env.EXPO_PUBLIC_API_URL ?? defaultApiUrl)
  setApiAuthMode("bearer")
}

configureApiClient()
