import { createApiClient, setDefaultApiClient } from "@zula/api"
import { refresh as apiRefresh } from "@zula/api/endpoints"

/** Cookie-mode client for Vite `/api` proxy. Call once at app boot. */
export function configureWebApiClient() {
  const client = createApiClient({
    baseUrl: "",
    authMode: "cookie",
    onUnauthorized: async () => {
      try {
        await apiRefresh()
        return true
      } catch {
        return false
      }
    },
  })
  setDefaultApiClient(client)
}
