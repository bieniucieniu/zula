import { setAccessTokenGetter, setApiBaseUrl } from "@zula/api"
import { getMemoryAccessToken } from "@/lib/token-store"

const defaultApiOrigin = "http://127.0.0.1:8000"

export function configureNativeApi() {
  setApiBaseUrl(process.env.EXPO_PUBLIC_API_URL ?? defaultApiOrigin)
  setAccessTokenGetter(() => getMemoryAccessToken())
}
